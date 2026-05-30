package com.tripplanning.images;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;

/**
 * Lazily creates one {@link ImpersonatedCredentials} instance per JVM for V4 signed URLs. Eager
 * creation at context startup can run before Workload Identity ADC is ready; per-request creation
 * was the previous working behaviour but is expensive under load.
 */
@Component
class GcsUrlSignerHolder {

    private static final List<String> CLOUD_PLATFORM_SCOPE =
            List.of("https://www.googleapis.com/auth/cloud-platform");

    private final String impersonateServiceAccount;
    private volatile ImpersonatedCredentials signer;

    GcsUrlSignerHolder(
            @Value("${spring.cloud.gcp.impersonate-service-account:}") String impersonateServiceAccount) {
        this.impersonateServiceAccount =
                impersonateServiceAccount == null ? "" : impersonateServiceAccount.trim();
    }

    boolean isConfigured() {
        return !impersonateServiceAccount.isEmpty();
    }

    ImpersonatedCredentials signer() {
        if (!isConfigured()) {
            return null;
        }
        ImpersonatedCredentials local = signer;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (signer != null) {
                return signer;
            }
            try {
                GoogleCredentials source =
                        GoogleCredentials.getApplicationDefault().createScoped(CLOUD_PLATFORM_SCOPE);
                signer =
                        ImpersonatedCredentials.create(
                                source, impersonateServiceAccount, null, CLOUD_PLATFORM_SCOPE, 300);
                return signer;
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Could not initialize GCS URL signing credentials for "
                                + impersonateServiceAccount,
                        e);
            }
        }
    }
}
