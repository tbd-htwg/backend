package com.tripplanning.platform.infra;

import com.tripplanning.platform.tenant.TenantTier;

public record TenantDispatchPayload(
    String slug,
    String displayName,
    TenantTier tier,
    String identityPlatformTenantId,
    String imageTag) {}
