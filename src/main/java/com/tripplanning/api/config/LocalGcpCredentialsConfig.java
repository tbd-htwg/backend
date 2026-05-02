package com.tripplanning.api.config;

import java.io.IOException;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.NoCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.spring.autoconfigure.storage.GcpStorageAutoConfiguration;

import lombok.extern.slf4j.Slf4j;

/**
 * Lokales Profil: {@link CredentialsProvider} bleibt {@link NoCredentialsProvider}, damit der
 * Firestore-Client über {@code FIRESTORE_EMULATOR_HOST} ohne echte GCP-Credentials zum Emulator geht.
 *
 * <p>{@link Storage} ist davon unabhängig: wenn {@linkplain GoogleCredentials#getApplicationDefault()
 * Application Default Credentials} verfügbar sind (z.&nbsp;B. {@code gcloud auth application-default
 * login}), nutzen wir sie für echte Bucket-Zugriffe (Signed URLs, {@code storage.objects.get} für
 * Pre-Upload-Registrierung). Ohne ADC fällt Storage auf einen No-Credentials-Stub zurück — dann meldet
 * GCS „Anonymous caller“ gegen einen produktiven Bucket.
 */
@Slf4j
@Configuration
@Profile("local")
@EnableAutoConfiguration(exclude = GcpStorageAutoConfiguration.class)
public class LocalGcpCredentialsConfig {

    @Bean
    @Primary
    public CredentialsProvider googleCredentials() {
        return NoCredentialsProvider.create();
    }

    @Bean
    @Primary
    public Storage storage() {
        try {
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
            return StorageOptions.newBuilder().setCredentials(credentials).build().getService();
        } catch (IOException e) {
            log.warn(
                    "Local profile: could not load Application Default Credentials for Cloud Storage ({}). "
                            + "Using a no-credentials Storage client; calls to a real GCS bucket will fail. "
                            + "For sample-image seeding or signed URLs against GCP, run: gcloud auth application-default login",
                    e.getMessage());
            return StorageOptions.newBuilder()
                    .setProjectId("tripplanning-local")
                    .setCredentials(NoCredentials.getInstance())
                    .build()
                    .getService();
        }
    }
}
