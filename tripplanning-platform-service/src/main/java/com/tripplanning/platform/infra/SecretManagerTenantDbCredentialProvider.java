package com.tripplanning.platform.infra;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.tripplanning.platform.tenant.TenantEntity;
import com.tripplanning.platform.tenant.TenantNaming;
import com.tripplanning.platform.tenant.TenantTier;

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

    if (tier == TenantTier.FREE || tier == null) {
      return new DbCredentials(user, "");
    }
    if (projectId == null || projectId.isBlank()) {
      throw new IllegalStateException(
          "tripplanning.platform.gcp.project-id is required for tenant DB credentials");
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
      throw new IllegalStateException(
          "Failed to read DB password for tenant " + tenant.getSlug() + " from " + secretId, e);
    }
  }
}
