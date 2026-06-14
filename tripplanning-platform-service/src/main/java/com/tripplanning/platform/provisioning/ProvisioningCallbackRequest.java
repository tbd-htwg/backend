package com.tripplanning.platform.provisioning;

import jakarta.validation.constraints.NotBlank;

public record ProvisioningCallbackRequest(
    @NotBlank String status,
    String message,
    String failedStep,
    String gcsBucket,
    String firestoreDatabase,
    String identityPlatformTenantId,
    String dbName,
    String dbUser) {}
