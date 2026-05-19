package com.tripplanning.trip;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.web.client.RestTemplate;

import com.tripplanning.common.auth.AuthProperties;
import com.tripplanning.common.client.RestSocialServiceClient;
import com.tripplanning.common.client.ServiceClientConfig;
import com.tripplanning.common.client.SocialServiceClient;
import com.tripplanning.common.config.ServiceClientProperties;

@SpringBootApplication(scanBasePackages = {"com.tripplanning", "com.tripplanning.common"})
@EntityScan(basePackages = {
    "com.tripplanning.trip",
    "com.tripplanning.user",
    "com.tripplanning.accommodation",
    "com.tripplanning.transport",
    "com.tripplanning.location",
    "com.tripplanning.tripLocation"
})
@EnableJpaRepositories(basePackages = {
    "com.tripplanning.trip",
    "com.tripplanning.user",
    "com.tripplanning.accommodation",
    "com.tripplanning.transport",
    "com.tripplanning.location",
    "com.tripplanning.tripLocation"
})
@EnableConfigurationProperties({AuthProperties.class, ServiceClientProperties.class})
@org.springframework.context.annotation.Import(ServiceClientConfig.class)
public class TripServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TripServiceApplication.class, args);
    }

    @Bean
    SocialServiceClient socialServiceClient(
            RestTemplate serviceRestTemplate, ServiceClientProperties properties) {
        return new RestSocialServiceClient(serviceRestTemplate, properties);
    }
}
