package com.tripplanning.platform.branding;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.tripplanning.platform.tenant.TenantEntity;
import com.tripplanning.platform.tenant.TenantNaming;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BrandingIconService {

  private static final Duration SIGNED_URL_TTL = Duration.ofMinutes(15);
  private static final Duration STUB_UPLOAD_TTL = Duration.ofMinutes(15);

  private final Storage storage;
  private final GcsUrlSignerHolder gcsUrlSignerHolder;
  private final Map<String, PendingStubUpload> pendingStubUploads = new ConcurrentHashMap<>();

  @Value("${spring.cloud.gcp.storage.bucket-name:}")
  private String defaultBucketName;

  @PostConstruct
  void trimBucketName() {
    if (defaultBucketName != null) {
      defaultBucketName = defaultBucketName.trim();
    }
  }

  public boolean usesStubStorage(TenantEntity tenant) {
    return !hasUsableBucket(tenant);
  }

  public SignedUploadInfo createSignedUpload(TenantEntity tenant, String fileName, String contentType) {
    if (contentType == null || !contentType.startsWith("image/")) {
      throw new IllegalArgumentException("Only image files are allowed");
    }
    if (usesStubStorage(tenant)) {
      return createStubUpload(tenant, contentType);
    }

    StorageTarget target = resolveStorage(tenant);
    String safeName = sanitizeFileName(fileName);
    String folder = "branding-icons/" + tenant.getId();
    String objectName = target.prefix() + folder + "/" + UUID.randomUUID() + "_" + safeName;

    BlobInfo blobInfo =
        BlobInfo.newBuilder(BlobId.of(target.bucket(), objectName))
            .setContentType(contentType)
            .build();

    URL signedUrl = createSignedUrl(blobInfo, HttpMethod.PUT);
    return new SignedUploadInfo(signedUrl.toString(), objectName, contentType);
  }

  public SignedUploadInfo createStubUpload(TenantEntity tenant, String contentType) {
    purgeExpiredStubUploads();
    String token = UUID.randomUUID().toString();
    pendingStubUploads.put(
        token, new PendingStubUpload(tenant.getId(), contentType, Instant.now().plus(STUB_UPLOAD_TTL)));
    String uploadUrl =
        "/api/v2/admin/tenants/" + tenant.getId() + "/branding/icon/stub-upload/" + token;
    return new SignedUploadInfo(uploadUrl, "stub-pending:" + token, contentType);
  }

  public String completeStubUpload(String tenantId, String token, byte[] body) {
    purgeExpiredStubUploads();
    PendingStubUpload pending = pendingStubUploads.remove(token);
    if (pending == null) {
      throw new IllegalArgumentException("Upload token expired or invalid");
    }
    if (!pending.tenantId().equals(tenantId)) {
      throw new IllegalArgumentException("Upload token does not match tenant");
    }
    if (body == null || body.length == 0) {
      throw new IllegalArgumentException("Upload body is empty");
    }
    if (body.length > 512 * 1024) {
      throw new IllegalArgumentException("Branding icon must be at most 512 KB in local stub mode");
    }
    return "data:" + pending.contentType() + ";base64," + Base64.getEncoder().encodeToString(body);
  }

  public String resolveIconUrl(TenantEntity tenant, String iconUrl) {
    if (iconUrl == null || iconUrl.isBlank()) {
      return null;
    }
    String trimmed = iconUrl.trim();
    if (trimmed.startsWith("data:")
        || trimmed.startsWith("http://")
        || trimmed.startsWith("https://")
        || trimmed.startsWith("/")) {
      return trimmed;
    }
    if (trimmed.startsWith("stub-pending:") || !hasUsableBucket(tenant)) {
      return null;
    }
    try {
      StorageTarget target = resolveStorage(tenant);
      String resolved = qualifyObjectPath(target, trimmed);
      BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(target.bucket(), resolved)).build();
      return createSignedUrl(blobInfo, HttpMethod.GET).toString();
    } catch (IllegalStateException e) {
      return null;
    }
  }

  public void deleteStoredIcon(TenantEntity tenant, String objectName) {
    if (objectName == null || objectName.isBlank()) {
      return;
    }
    String trimmed = objectName.trim();
    if (trimmed.startsWith("data:")
        || trimmed.startsWith("http://")
        || trimmed.startsWith("https://")
        || trimmed.startsWith("/")
        || trimmed.startsWith("stub-pending:")) {
      return;
    }
    if (!hasUsableBucket(tenant)) {
      return;
    }
    StorageTarget target = resolveStorage(tenant);
    String prefix = target.prefix() + "branding-icons/" + tenant.getId() + "/";
    if (!qualifyObjectPath(target, trimmed).startsWith(prefix)) {
      return;
    }
    String resolved = qualifyObjectPath(target, trimmed);
    try {
      storage.delete(BlobId.of(target.bucket(), resolved));
    } catch (StorageException ignored) {
      // Best-effort cleanup when replacing branding icons.
    }
  }

  private boolean hasUsableBucket(TenantEntity tenant) {
    String bucket =
        tenant.getGcsBucket() != null && !tenant.getGcsBucket().isBlank()
            ? tenant.getGcsBucket()
            : defaultBucketName;
    return bucket != null && !bucket.isBlank();
  }

  private void purgeExpiredStubUploads() {
    Instant now = Instant.now();
    pendingStubUploads.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
  }

  private URL createSignedUrl(BlobInfo blobInfo, HttpMethod method) {
    List<Storage.SignUrlOption> options = new ArrayList<>();
    options.add(Storage.SignUrlOption.httpMethod(method));
    options.add(Storage.SignUrlOption.withV4Signature());
    if (method == HttpMethod.PUT) {
      options.add(Storage.SignUrlOption.withContentType());
    }
    if (gcsUrlSignerHolder.isConfigured()) {
      options.add(Storage.SignUrlOption.signWith(gcsUrlSignerHolder.signer()));
    }
    try {
      return storage.signUrl(
          blobInfo,
          SIGNED_URL_TTL.toMinutes(),
          TimeUnit.MINUTES,
          options.toArray(Storage.SignUrlOption[]::new));
    } catch (Exception e) {
      throw new IllegalStateException(
          "Could not sign GCS URL. Configure spring.cloud.gcp.impersonate-service-account "
              + "or signer-capable ADC credentials.",
          e);
    }
  }

  private StorageTarget resolveStorage(TenantEntity tenant) {
    String bucket =
        tenant.getGcsBucket() != null && !tenant.getGcsBucket().isBlank()
            ? tenant.getGcsBucket()
            : defaultBucketName;
    if (bucket == null || bucket.isBlank()) {
      throw new IllegalStateException("Tenant has no GCS bucket configured for branding icon upload");
    }
    String prefix = TenantNaming.objectPrefix(tenant.getSlug(), tenant.getTier());
    return new StorageTarget(bucket, prefix == null ? "" : prefix);
  }

  private static String qualifyObjectPath(StorageTarget target, String objectName) {
    if (objectName == null || objectName.isBlank()) {
      return objectName;
    }
    String prefix = target.prefix();
    if (prefix == null || prefix.isBlank() || objectName.startsWith(prefix)) {
      return objectName;
    }
    return prefix + objectName;
  }

  private static String sanitizeFileName(String fileName) {
    String candidate = fileName == null ? "image" : fileName.trim();
    if (candidate.isBlank()) {
      candidate = "image";
    }
    return candidate.replaceAll("[^a-zA-Z0-9._-]", "_");
  }

  private record StorageTarget(String bucket, String prefix) {}

  private record PendingStubUpload(String tenantId, String contentType, Instant expiresAt) {}

  public record SignedUploadInfo(String uploadUrl, String objectName, String contentType) {}
}
