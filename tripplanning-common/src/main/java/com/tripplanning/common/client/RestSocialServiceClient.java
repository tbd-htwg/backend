package com.tripplanning.common.client;

import java.util.List;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import com.tripplanning.common.config.ServiceClientProperties;
import com.tripplanning.common.internal.LikedTripIdsResponse;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RestSocialServiceClient implements SocialServiceClient {

    private final RestTemplate restTemplate;
    private final ServiceClientProperties properties;

    @Override
    public List<Long> getLikedTripIdsForUser(long userId) {
        LikedTripIdsResponse body =
                restTemplate.exchange(
                                properties.getSocialBaseUrl()
                                        + "/internal/users/{userId}/liked-trip-ids",
                                HttpMethod.GET,
                                new HttpEntity<>(internalHeaders()),
                                LikedTripIdsResponse.class,
                                userId)
                        .getBody();
        return body != null && body.tripIds() != null ? body.tripIds() : List.of();
    }

    private HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String secret = properties.getInternalSecret();
        if (secret != null && !secret.isBlank()) {
            headers.set("X-Internal-Secret", secret);
        }
        return headers;
    }
}
