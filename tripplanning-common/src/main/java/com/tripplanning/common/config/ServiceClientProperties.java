package com.tripplanning.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "tripplanning.services")
public class ServiceClientProperties {

    /** Base URL of trip-service, e.g. http://trip-service.tripplanning.svc.cluster.local:8080 */
    private String tripBaseUrl = "http://localhost:8080";

    /** Base URL of social-service, e.g. http://social-service.tripplanning.svc.cluster.local:8080 */
    private String socialBaseUrl = "http://localhost:8081";

    /** Optional shared secret for internal service-to-service calls. */
    private String internalSecret = "";
}
