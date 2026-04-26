package com.tripplanning.images;

import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;

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

        URL signedUrl = createSignedUrl(blobInfo);

        String objectUrl = String.format(
                "https://storage.googleapis.com/%s/%s",
                bucketName,
                java.net.URLEncoder.encode(objectName, StandardCharsets.UTF_8).replace("+", "%20"));

        return new SignedUploadInfo(signedUrl.toString(), objectUrl, objectName, contentType);
    }

    /**
     * If {@code objectUrl} is a public URL for this bucket and the decoded object name starts with
     * {@code requiredNamePrefix}, deletes that object. Otherwise does nothing (e.g. foreign URL or
     * blank).
     */
    public void deleteStoredObjectByUrlIfApplicable(String objectUrl, String requiredNamePrefix) {
        if (objectUrl == null || objectUrl.isBlank()) {
            return;
        }
        String objectName = parseObjectNameFromPublicUrl(objectUrl.trim());
        if (objectName == null || !objectName.startsWith(requiredNamePrefix)) {
            log.debug(
                    "Skip GCS delete: URL does not match configured bucket or prefix {} (url host/path mismatch)",
                    requiredNamePrefix);
            return;
        }
        try {
            boolean removed = Boolean.TRUE.equals(storage.delete(BlobId.of(bucketName, objectName)));
            if (removed) {
                log.debug("Deleted gs://{}/{}", bucketName, objectName);
            } else {
                log.debug("GCS object already absent gs://{}/{}", bucketName, objectName);
            }
        } catch (StorageException e) {
            log.warn(
                    "Could not delete gs://{}/{} — clearing DB image URL anyway. Fix IAM (storage.objects.delete) if objects should be removed. {} [{}]",
                    bucketName,
                    objectName,
                    e.getMessage(),
                    e.getCode(),
                    e);
        }
    }

    /**
     * @return object name within this bucket, or {@code null} if the URL does not target this bucket
     */
    private String parseObjectNameFromPublicUrl(String objectUrl) {
        if (bucketName == null || bucketName.isEmpty()) {
            return null;
        }
        // Path-style URL produced by this service
        String pathStylePrefix = "https://storage.googleapis.com/" + bucketName + "/";
        if (objectUrl.startsWith(pathStylePrefix)) {
            return URLDecoder.decode(
                    objectUrl.substring(pathStylePrefix.length()), StandardCharsets.UTF_8);
        }
        // Virtual-hosted style (also valid for public GCS URLs)
        String vhostPrefix = "https://" + bucketName + ".storage.googleapis.com/";
        if (objectUrl.startsWith(vhostPrefix)) {
            return URLDecoder.decode(objectUrl.substring(vhostPrefix.length()), StandardCharsets.UTF_8);
        }
        return null;
    }

    private URL createSignedUrl(BlobInfo blobInfo) {
        List<Storage.SignUrlOption> options = new ArrayList<>();
        options.add(Storage.SignUrlOption.httpMethod(HttpMethod.PUT));
        options.add(Storage.SignUrlOption.withV4Signature());
        options.add(Storage.SignUrlOption.withContentType());

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
            String objectUrl,
            String objectName,
            String contentType) {
    }
}