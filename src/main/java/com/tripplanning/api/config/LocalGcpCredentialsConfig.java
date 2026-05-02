package com.tripplanning.api.config;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.spring.autoconfigure.storage.GcpStorageAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Ersetzt alle GCP-Credentials im lokalen Profil durch NoCredentialsProvider,
 * damit Spring Cloud GCPs Auto-Konfiguration (inkl. firestoreTemplate) vollständig
 * durchläuft, ohne echte GCP-Zugangsdaten zu benötigen.
 * Der Firestore-SDK verbindet sich dann über FIRESTORE_EMULATOR_HOST zum Emulator.
 * GcpStorageAutoConfiguration wird ausgeschlossen und durch einen Stub ersetzt,
 * da Signed-URL-Generierung lokal nicht benötigt wird.
 */
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
        return StorageOptions.newBuilder()
                .setProjectId("tripplanning-local")
                .setCredentials(com.google.cloud.NoCredentials.getInstance())
                .build()
                .getService();
    }
}
