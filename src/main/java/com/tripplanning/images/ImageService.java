package com.tripplanning.images;

import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.tripplanning.tripLocation.TripLocationImageEntity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageService {

    private static final Duration SIGNED_UPLOAD_URL_TTL = Duration.ofMinutes(15);

    private final Storage storage;

    @Value("${spring.cloud.gcp.storage.bucket-name}")
    private String bucketName;

    @Value("${spring.cloud.gcp.impersonate-service-account:}")
    private String impersonateServiceAccount;

    @PostConstruct
    void trimBucketName() {
        if (bucketName != null) {
            bucketName = bucketName.trim();
        }
    }

    public SignedUploadInfo createSignedUpload(String folder, String fileName, String contentType) {
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Only image files (JPG, PNG) allowed!");
        }

        String safeName = sanitizeFileName(fileName);
        String objectName = folder + "/" + UUID.randomUUID() + "_" + safeName;

        BlobId blobId = BlobId.of(bucketName, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(contentType)
                .build();

        URL signedUrl = createSignedUrl(blobInfo, HttpMethod.PUT);

        return new SignedUploadInfo(signedUrl.toString(), objectName, contentType);
    }

    /** Create a signed read URL (GET) for the given object name in the configured bucket. */
    public String createSignedReadUrl(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            return null;
        }
        BlobId blobId = BlobId.of(bucketName, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
        return createSignedUrl(blobInfo, HttpMethod.GET).toString();
    }

    /** Signed read URL only for authenticated requests. */
    public String createSignedReadUrlIfAuthenticated(String objectName) {
        if (!isAuthenticatedJwtRequest()) {
            return null;
        }
        return createSignedReadUrl(objectName);
    }


    public void deleteStoredObjectByPath(String objectName, String requiredNamePrefix) {
        if (objectName == null || objectName.isBlank()) {
            return;
        }
        String trimmed = objectName.trim();
        if (trimmed == null || !trimmed.startsWith(requiredNamePrefix)) {
            log.debug("Skip GCS delete: object does not match required prefix {} (object={})", requiredNamePrefix, trimmed);
            return;
        }
        try {
            boolean removed = Boolean.TRUE.equals(storage.delete(BlobId.of(bucketName, trimmed)));
            if (removed) {
                log.debug("Deleted gs://{}/{}", bucketName, trimmed);
            } else {
                log.debug("GCS object already absent gs://{}/{}", bucketName, trimmed);
            }
        } catch (StorageException e) {
            log.warn(
                    "Could not delete gs://{}/{} — clearing DB image name anyway. Fix IAM (storage.objects.delete) if objects should be removed. {} [{}]",
                    bucketName,
                    trimmed,
                    e.getMessage(),
                    e.getCode(),
                    e);
        }
    }

    private URL createSignedUrl(BlobInfo blobInfo, HttpMethod method) {
        List<Storage.SignUrlOption> options = new ArrayList<>();
        options.add(Storage.SignUrlOption.httpMethod(method));
        options.add(Storage.SignUrlOption.withV4Signature());

        if (method == HttpMethod.PUT) {
            options.add(Storage.SignUrlOption.withContentType());
        }

        String targetServiceAccount = impersonateServiceAccount == null ? "" : impersonateServiceAccount.trim();
        if (!targetServiceAccount.isEmpty()) {
            try {
                GoogleCredentials sourceCredentials = GoogleCredentials.getApplicationDefault()
                        .createScoped("https://www.googleapis.com/auth/cloud-platform");
                ImpersonatedCredentials signerCredentials = ImpersonatedCredentials.create(
                        sourceCredentials,
                        targetServiceAccount,
                        null,
                        List.of("https://www.googleapis.com/auth/cloud-platform"),
                        300);
                options.add(Storage.SignUrlOption.signWith(signerCredentials));
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Could not initialize signing credentials for service account impersonation: "
                                + targetServiceAccount + ". Root cause: " + rootCauseMessage(e),
                        e);
            }
        }

        try {
            return storage.signUrl(
                    blobInfo,
                    SIGNED_UPLOAD_URL_TTL.toMinutes(),
                    TimeUnit.MINUTES,
                    options.toArray(Storage.SignUrlOption[]::new));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not sign upload URL. Configure spring.cloud.gcp.impersonate-service-account "
                            + "or provide signer-capable ADC credentials. Root cause: "
                            + rootCauseMessage(e),
                    e);
        }
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() != null ? current.getMessage() : current.getClass().getSimpleName();
    }

    private String sanitizeFileName(String fileName) {
        String candidate = fileName == null ? "image" : fileName.trim();
        if (candidate.isBlank()) {
            candidate = "image";
        }
        return candidate.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public record SignedUploadInfo(
            String uploadUrl,
            String objectName,
            String contentType) {
    }


    //needed for projection
    public List<String> getSignedUrlsForImages(List<TripLocationImageEntity> images) {
    if (images == null) return List.of();
    return images.stream()
            .map(img -> createSignedReadUrl(img.getImagePath()))
            .filter(url -> url != null)
            .toList();
    }

    private boolean isAuthenticatedJwtRequest() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getPrincipal() instanceof Jwt;
    }
}