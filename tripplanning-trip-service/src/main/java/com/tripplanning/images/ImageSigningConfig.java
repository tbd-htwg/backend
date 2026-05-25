package com.tripplanning.images;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ImageSigningConfig {

    private static final int SIGNING_POOL_SIZE = 12;

    @Bean(destroyMethod = "shutdown")
    ExecutorService imageSigningExecutor() {
        return Executors.newFixedThreadPool(SIGNING_POOL_SIZE);
    }
}
