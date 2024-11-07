package com.example.loadbalancer.controller;

import com.example.common.dto.HeartbeatRequest;
import com.example.common.dto.HeartbeatResponse;
import com.example.loadbalancer.service.HealthCheckService;
import com.example.loadbalancer.service.LoadBalancerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Enumeration;

@Slf4j
@RestController
public class ProxyController {
    private final LoadBalancerService loadBalancerService;
    private final HealthCheckService healthCheckService;
    private final RestTemplate restTemplate;

    public ProxyController(
            LoadBalancerService loadBalancerService,
            HealthCheckService healthCheckService,
            RestTemplate restTemplate
    ) {
        this.loadBalancerService = loadBalancerService;
        this.healthCheckService = healthCheckService;
        this.restTemplate = restTemplate;
    }

    @PostMapping("/heartbeat")
    public HeartbeatResponse handleHeartbeat(@RequestBody HeartbeatRequest request) {
        return healthCheckService.processHeartbeat(request);
    }

    @GetMapping("/favicon.ico")
    public ResponseEntity<Void> favicon() {
        return ResponseEntity.notFound().build();
    }

    @RequestMapping(value = "/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.DELETE, RequestMethod.PATCH})
    public ResponseEntity<?> proxyRequest(HttpServletRequest request) throws URISyntaxException, IOException {
        // Skip favicon.ico requests
        if (request.getRequestURI().equals("/favicon.ico")) {
            return ResponseEntity.notFound().build();
        }

        var node = loadBalancerService.getNextAvailableNode();
        String targetUrl = String.format("http://%s:%d%s",
                node.host(),
                node.port(),
                request.getRequestURI()
        );

        // Add query string if present
        String queryString = request.getQueryString();
        if (queryString != null) {
            targetUrl += "?" + queryString;
        }

        log.info("Forwarding request to: {}", targetUrl);

        try {
            // Copy headers
            HttpHeaders headers = new HttpHeaders();
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                List<String> headerValues = Collections.list(request.getHeaders(headerName));
                headers.addAll(headerName, headerValues);
            }

            // Get the request body if present
            byte[] body = StreamUtils.copyToByteArray(request.getInputStream());

            // Create the entity with headers and body
            HttpEntity<byte[]> httpEntity = new HttpEntity<>(body, headers);

            // Forward the request
            ResponseEntity<String> response = restTemplate.exchange(
                    new URI(targetUrl),
                    HttpMethod.valueOf(request.getMethod()),
                    httpEntity,
                    String.class
            );

            // Copy response headers
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.putAll(response.getHeaders());

            return new ResponseEntity<>(response.getBody(), responseHeaders, response.getStatusCode());
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Resource not found at {}: {}", targetUrl, e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error forwarding request to {}: {}", targetUrl, e.getMessage());
            throw e;
        }
    }
}
