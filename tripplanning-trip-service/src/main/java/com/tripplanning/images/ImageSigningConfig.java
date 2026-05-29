package com.tripplanning.images;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;

@Configuration
class ImageSigningConfig {

    private static final int SIGNING_POOL_SIZE = 12;
    private static final List<String> CLOUD_PLATFORM_SCOPE =
            List.of("https://www.googleapis.com/auth/cloud-platform");

    @Bean(destroyMethod = "shutdown")
    ExecutorService imageSigningExecutor() {
        return Executors.newFixedThreadPool(SIGNING_POOL_SIZE);
    }

    /**
     * Reused for every V4 signed URL (impersonation token refresh is handled inside the credentials
     * implementation). Empty when {@code spring.cloud.gcp.impersonate-service-account} is unset.
     */
    @Bean
    Optional<GoogleCredentials> gcsSigningCredentials(
            @Value("${spring.cloud.gcp.impersonate-service-account:}") String impersonateServiceAccount)
            throws IOException {
        String target = impersonateServiceAccount == null ? "" : impersonateServiceAccount.trim();
        if (target.isEmpty()) {
            return Optional.empty();
        }
        GoogleCredentials source =
                GoogleCredentials.getApplicationDefault().createScoped(CLOUD_PLATFORM_SCOPE);
        return Optional.of(
                ImpersonatedCredentials.create(source, target, null, CLOUD_PLATFORM_SCOPE, 300));
    }
}
