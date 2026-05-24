package com.tripplanning.seed;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableConfigurationProperties(SeedProperties.class)
@EnableTransactionManagement
public class SeedJobApplication {

    public static void main(String[] args) {
        int code = SpringApplication.exit(SpringApplication.run(SeedJobApplication.class, args));
        System.exit(code);
    }
}
