package com.tripplanning.common.client;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import com.tripplanning.common.config.ServiceClientProperties;

@Configuration
@EnableConfigurationProperties(ServiceClientProperties.class)
public class ServiceClientConfig {

    @Bean
    RestTemplate serviceRestTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(10))
                .build();
    }

}
