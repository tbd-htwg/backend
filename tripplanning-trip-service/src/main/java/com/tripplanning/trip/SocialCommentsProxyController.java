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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.tripplanning.common.config.ServiceClientProperties;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Forwards {@code /api/v2/comments} to social-service. On GKE the Gateway routes this path directly;
 * Minikube and single-host dev proxy through trip-service like {@link SocialUserLikesProxyController}.
 */
@RestController
@RequiredArgsConstructor
public class SocialCommentsProxyController {

    private static final List<String> FORWARD_HEADERS =
            List.of("Authorization", "Accept", "Content-Type", "If-None-Match", "If-Match");

    private final RestTemplate serviceRestTemplate;
    private final ServiceClientProperties serviceClientProperties;

    @PostMapping("/api/v2/comments")
    public ResponseEntity<byte[]> createComment(HttpServletRequest request) throws IOException {
        return forward(request, "/api/v2/comments");
    }

    @GetMapping("/api/v2/comments/search/findByTripIdOrderByCreatedAtDesc")
    public ResponseEntity<byte[]> findByTrip(HttpServletRequest request) throws IOException {
        return forward(request, "/api/v2/comments/search/findByTripIdOrderByCreatedAtDesc");
    }

    @DeleteMapping("/api/v2/comments/{id}")
    public ResponseEntity<byte[]> deleteComment(
            @PathVariable String id, HttpServletRequest request) throws IOException {
        return forward(request, "/api/v2/comments/" + id);
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
