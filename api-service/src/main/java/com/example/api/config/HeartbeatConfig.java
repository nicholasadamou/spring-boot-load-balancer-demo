package com.example.api.config;

import com.example.common.dto.HeartbeatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
public class HeartbeatConfig {
    private final RestTemplate restTemplate;
    private final String serviceId = UUID.randomUUID().toString();

    @Value("${server.port}")
    private int serverPort;

    @Value("${loadbalancer.url}")
    private String loadBalancerUrl;

    public HeartbeatConfig() {
        this.restTemplate = new RestTemplate();
    }

    @Scheduled(fixedRate = 5000) // Send heartbeat every 5 seconds
    public void sendHeartbeat() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();

            var request = new HeartbeatRequest(
                    serviceId,
                    hostname,
                    serverPort,
                    "UP",
                    Instant.now().toEpochMilli()
            );

            restTemplate.postForObject(
                    loadBalancerUrl + "/heartbeat",
                    request,
                    void.class
            );

            log.info("Heartbeat sent successfully to {}", loadBalancerUrl);
        } catch (Exception e) {
            log.error("Failed to send heartbeat: {}", e.getMessage());
        }
    }
}