package com.tripplanning.externalinfo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Import;

import com.tripplanning.common.auth.AppJwtDecoderConfiguration;
import com.tripplanning.common.auth.AuthProperties;

@SpringBootApplication(
        exclude = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
@EnableConfigurationProperties(AuthProperties.class)
@Import(AppJwtDecoderConfiguration.class)
@EnableCaching
public class ExternalInfoServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExternalInfoServiceApplication.class, args);
    }
}
