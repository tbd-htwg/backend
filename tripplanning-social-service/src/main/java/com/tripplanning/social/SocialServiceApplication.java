package com.tripplanning.social;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import com.google.cloud.spring.data.firestore.repository.config.EnableReactiveFirestoreRepositories;
import com.tripplanning.common.auth.AuthProperties;
import com.tripplanning.common.client.RestTripServiceClient;
import com.tripplanning.common.client.ServiceClientConfig;
import com.tripplanning.common.client.TripServiceClient;
import com.tripplanning.common.config.ServiceClientProperties;

@SpringBootApplication(scanBasePackages = {"com.tripplanning.social", "com.tripplanning.common"})
@EnableReactiveFirestoreRepositories(basePackages = "com.tripplanning.social")
@EnableConfigurationProperties({AuthProperties.class, ServiceClientProperties.class})
@org.springframework.context.annotation.Import(ServiceClientConfig.class)
public class SocialServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SocialServiceApplication.class, args);
    }

    @Bean
    TripServiceClient tripServiceClient(
            RestTemplate serviceRestTemplate, ServiceClientProperties properties) {
        return new RestTripServiceClient(serviceRestTemplate, properties);
    }
}
