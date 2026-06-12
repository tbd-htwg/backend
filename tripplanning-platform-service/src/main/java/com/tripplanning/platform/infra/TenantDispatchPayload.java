package com.tripplanning.platform.infra;

import com.tripplanning.platform.tenant.TenantTier;

public record TenantDispatchPayload(
    String slug,
    String displayName,
    TenantTier tier,
    String hostUrl,
    String namespace,
    String dbName,
    String searchIndex,
    String frontendPath,
    String identityPlatformTenantId,
    String imageTag,
    String gcsBucket) {}
