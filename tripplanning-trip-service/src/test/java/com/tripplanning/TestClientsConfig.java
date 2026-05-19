package com.tripplanning;

import java.util.List;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.tripplanning.common.client.SocialServiceClient;

@TestConfiguration
public class TestClientsConfig {

    @Bean
    @Primary
    SocialServiceClient mockSocialServiceClient() {
        return userId -> List.of();
    }
}
