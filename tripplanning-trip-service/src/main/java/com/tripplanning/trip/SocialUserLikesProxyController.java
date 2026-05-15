package com.tripplanning.trip;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.tripplanning.common.config.ServiceClientProperties;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Forwards like/unlike/HEAD checks to social-service. GKE {@code gke-l7-global-external-managed}
 * HTTPRoute cannot match on HTTP method; all {@code /api/v2} goes to trip-service, so mutations
 * are proxied here (GET {@code /likedTrips} stays on {@link TripLikedTripsController}).
 */
@RestController
@RequiredArgsConstructor
public class SocialUserLikesProxyController {

    private static final List<String> FORWARD_HEADERS =
            List.of("Authorization", "Accept", "Content-Type", "If-None-Match", "If-Match");

    private final RestTemplate serviceRestTemplate;
    private final ServiceClientProperties serviceClientProperties;

    @PostMapping(
            value = "/api/v2/users/{userId}/likedTrips",
            consumes = {"text/uri-list", "application/json"})
    public ResponseEntity<byte[]> likeTrip(
            @PathVariable Long userId, HttpServletRequest request) throws IOException {
        return forward(request, "/api/v2/users/" + userId + "/likedTrips");
    }

    @DeleteMapping("/api/v2/users/{userId}/likedTrips/{tripId}")
    public ResponseEntity<byte[]> unlikeTrip(
            @PathVariable Long userId,
            @PathVariable Long tripId,
            HttpServletRequest request)
            throws IOException {
        return forward(
                request, "/api/v2/users/" + userId + "/likedTrips/" + tripId);
    }

    @RequestMapping(
            method = RequestMethod.HEAD,
            value = "/api/v2/users/{userId}/likedTrips/{tripId}")
    public ResponseEntity<byte[]> likeExists(
            @PathVariable Long userId,
            @PathVariable Long tripId,
            HttpServletRequest request)
            throws IOException {
        return forward(
                request, "/api/v2/users/" + userId + "/likedTrips/" + tripId);
    }

    private ResponseEntity<byte[]> forward(HttpServletRequest request, String socialPath)
            throws IOException {
        URI target =
                UriComponentsBuilder.fromHttpUrl(serviceClientProperties.getSocialBaseUrl())
                        .path(socialPath)
                        .query(request.getQueryString())
                        .build(true)
                        .toUri();

        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        byte[] body = request.getInputStream().readAllBytes();
        HttpEntity<byte[]> entity = new HttpEntity<>(body.length > 0 ? body : null, copyHeaders(request));

        ResponseEntity<byte[]> response =
                serviceRestTemplate.exchange(target, method, entity, byte[].class);
        HttpHeaders out = new HttpHeaders();
        response.getHeaders().forEach((k, v) -> {
            if (!HttpHeaders.TRANSFER_ENCODING.equalsIgnoreCase(k)) {
                out.put(k, v);
            }
        });
        return new ResponseEntity<>(response.getBody(), out, response.getStatusCode());
    }

    private static HttpHeaders copyHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        for (String name : FORWARD_HEADERS) {
            Enumeration<String> values = request.getHeaders(name);
            if (values != null) {
                Collections.list(values).forEach(v -> headers.add(name, v));
            }
        }
        return headers;
    }
}
