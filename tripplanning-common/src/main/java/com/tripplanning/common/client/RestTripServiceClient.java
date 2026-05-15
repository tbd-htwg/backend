package com.tripplanning.common.client;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.tripplanning.common.config.ServiceClientProperties;
import com.tripplanning.common.internal.InternalUserDto;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RestTripServiceClient implements TripServiceClient {

    private final RestTemplate restTemplate;
    private final ServiceClientProperties properties;

    @Override
    public boolean tripExists(long tripId) {
        try {
            restTemplate.exchange(
                    properties.getTripBaseUrl() + "/internal/trips/{id}",
                    HttpMethod.HEAD,
                    new HttpEntity<>(internalHeaders()),
                    Void.class,
                    tripId);
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        }
    }

    @Override
    public Map<Long, InternalUserDto> getUsersByIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        String ids =
                userIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        InternalUserDto[] users =
                restTemplate.exchange(
                                properties.getTripBaseUrl() + "/internal/users?ids={ids}",
                                HttpMethod.GET,
                                new HttpEntity<>(internalHeaders()),
                                InternalUserDto[].class,
                                ids)
                        .getBody();
        if (users == null) {
            return Map.of();
        }
        return Arrays.stream(users).collect(Collectors.toMap(InternalUserDto::id, u -> u));
    }

    @Override
    public java.util.Optional<Long> getTripOwnerUserId(long tripId) {
        try {
            ResponseEntity<Long> response =
                    restTemplate.exchange(
                            properties.getTripBaseUrl() + "/internal/trips/{id}/owner-user-id",
                            HttpMethod.GET,
                            new HttpEntity<>(internalHeaders()),
                            Long.class,
                            tripId);
            return java.util.Optional.ofNullable(response.getBody());
        } catch (HttpClientErrorException.NotFound e) {
            return java.util.Optional.empty();
        }
    }

    @Override
    public boolean isTripOwnedBy(long tripId, long userId) {
        return getTripOwnerUserId(tripId).map(ownerId -> ownerId == userId).orElse(false);
    }

    @Override
    public void evictLikedByFeedCache() {
        restTemplate.exchange(
                properties.getTripBaseUrl() + "/internal/cache/trips/liked-by/evict",
                HttpMethod.POST,
                new HttpEntity<>(internalHeaders()),
                Void.class);
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
