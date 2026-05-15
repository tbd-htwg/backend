package com.tripplanning.common.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "tripplanning.auth")
public class AuthProperties {

    private String firebaseProjectId;
    private String jwtSecret;
    private long jwtExpirationSeconds = 43_200L;
    private String testBearerToken = "";

    public void setTestBearerToken(String testBearerToken) {
        this.testBearerToken = testBearerToken == null ? "" : testBearerToken;
    }
}
