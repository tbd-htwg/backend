package com.tripplanning.images;

import java.net.URL;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
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
    private final ExecutorService imageSigningExecutor;
    private final Optional<GoogleCredentials> gcsSigningCredentials;

    @Value("${spring.cloud.gcp.storage.bucket-name}")
    private String bucketName;

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

    /**
     * Empty if the object exists (metadata readable); otherwise a short reason suitable for an HTTP body.
     * Distinguishes “wrong key / missing object” from IAM or API errors (often misread as generic 404s).
     */
    public Optional<String> objectExistenceProblem(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            return Optional.of("imagePath is empty.");
        }
        String trimmed = Normalizer.normalize(objectName.trim(), Normalizer.Form.NFC);
        BlobId blobId = BlobId.of(bucketName, trimmed);
        try {
            Blob blob = storage.get(blobId);
            if (blob != null) {
                return Optional.empty();
            }
            return Optional.of(
                    "No object at gs://"
                            + bucketName
                            + "/"
                            + trimmed
                            + " (metadata GET returned null; check rsync prefix vs --sample-images-prefix and bucket "
                            + "spring.cloud.gcp.storage.bucket-name).");
        } catch (StorageException e) {
            log.warn(
                    "GCS metadata read failed for gs://{}/{}: {} [{}]",
                    bucketName,
                    trimmed,
                    e.getMessage(),
                    e.getCode());
            return Optional.of(
                    "Could not read gs://"
                            + bucketName
                            + "/"
                            + trimmed
                            + ": "
                            + e.getMessage()
                            + " (grant storage.objects.get on the bucket to the Cloud Run runtime SA; code="
                            + e.getCode()
                            + ").");
        }
    }

    /** Returns whether the given object name exists in the configured bucket (for pre-uploaded seeds). */
    public boolean objectExistsInBucket(String objectName) {
        return objectExistenceProblem(objectName).isEmpty();
    }

    /** Create a signed read URL (GET) for the given object name in the configured bucket. */
    public String createSignedReadUrl(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            return null;
        }
        String trimmed = objectName.trim();
        // Google login stores the OIDC "picture" claim here; it is already an HTTPS URL, not a GCS key.
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        BlobId blobId = BlobId.of(bucketName, trimmed);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
        return createSignedUrl(blobInfo, HttpMethod.GET).toString();
    }

    /** Signed read URL only for authenticated requests. */
    public String createSignedReadUrlIfAuthenticated(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            return null;
        }
        String trimmed = objectName.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        if (!isAuthenticatedJwtRequest()) {
            return null;
        }
        return createSignedReadUrl(objectName);
    }

    /**
     * Signs many read URLs in parallel (bounded pool). Preserves input order; entries that cannot be
     * signed are omitted from the result positions by returning null at that index — callers should
     * filter nulls when building lists.
     */
    public List<String> createSignedReadUrlsIfAuthenticated(List<String> objectNames) {
        if (objectNames == null || objectNames.isEmpty()) {
            return List.of();
        }
        if (!isAuthenticatedJwtRequest()) {
            return Collections.nCopies(objectNames.size(), null);
        }
        List<Future<String>> futures = new ArrayList<>(objectNames.size());
        for (String objectName : objectNames) {
            futures.add(imageSigningExecutor.submit(() -> signReadUrlUnchecked(objectName)));
        }
        List<String> out = new ArrayList<>(objectNames.size());
        for (Future<String> future : futures) {
            try {
                out.add(future.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while signing image URLs", e);
            } catch (ExecutionException e) {
                throw new IllegalStateException("Failed to sign image URL", e.getCause());
            }
        }
        return out;
    }

    /** Whether the current request may receive signed GCS read URLs. */
    public boolean isAuthenticatedForSigning() {
        return isAuthenticatedJwtRequest();
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

        gcsSigningCredentials.ifPresent(
                creds -> options.add(Storage.SignUrlOption.signWith(creds)));

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

    /** Signs a read URL without reading {@link SecurityContextHolder} (for use from signing pool threads). */
    private String signReadUrlUnchecked(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            return null;
        }
        String trimmed = objectName.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        return createSignedReadUrl(objectName);
    }

    private boolean isAuthenticatedJwtRequest() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getPrincipal() instanceof Jwt;
    }
}