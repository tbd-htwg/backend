package com.tripplanning.platform.infra;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.tripplanning.platform.tenant.TenantEntity;
import com.tripplanning.platform.tenant.TenantNaming;
import com.tripplanning.platform.tenant.TenantTier;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SecretManagerTenantDbCredentialProvider implements TenantDbCredentialProvider {

  private final String projectId;

  public SecretManagerTenantDbCredentialProvider(
      @Value("${tripplanning.platform.gcp.project-id:}") String projectId) {
    this.projectId = projectId;
  }

  @Override
  public DbCredentials resolve(TenantEntity tenant) {
    TenantTier tier = tenant.getTier();
    String user =
        tenant.getDbUser() != null && !tenant.getDbUser().isBlank()
            ? tenant.getDbUser()
            : TenantNaming.dbUser(tenant.getSlug(), tier);

    if (projectId == null
        || projectId.isBlank()
        || tier == TenantTier.FREE
        || tier == null) {
      return new DbCredentials(user, "");
    }

    String secretId = TenantNaming.dbPasswordSecretId(tenant.getSlug(), tier);
    if (secretId == null) {
      return new DbCredentials(user, "");
    }

    try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
      SecretVersionName versionName = SecretVersionName.of(projectId, secretId, "latest");
      String password =
          client.accessSecretVersion(versionName).getPayload().getData().toStringUtf8();
      return new DbCredentials(user, password);
    } catch (Exception e) {
      log.warn(
          "Failed to read DB password for tenant {} from {}: {}",
          tenant.getSlug(),
          secretId,
          e.getMessage());
      return new DbCredentials(user, "");
    }
  }
}
