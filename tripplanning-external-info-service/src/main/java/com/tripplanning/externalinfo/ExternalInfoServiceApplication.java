package com.tripplanning.externalinfo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class ExternalInfoServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExternalInfoServiceApplication.class, args);
    }
}
