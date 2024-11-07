package com.example.loadbalancer.service;

import com.example.common.dto.HeartbeatRequest;
import com.example.common.dto.HeartbeatResponse;
import com.example.loadbalancer.model.ServiceNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@Slf4j
public class HealthCheckService {
    private final LoadBalancerService loadBalancerService;
    private static final long HEALTH_CHECK_TIMEOUT_SECONDS = 30;

    public HealthCheckService(LoadBalancerService loadBalancerService) {
        this.loadBalancerService = loadBalancerService;
    }

    public HeartbeatResponse processHeartbeat(HeartbeatRequest request) {
        ServiceNode node = new ServiceNode(
                request.serviceId(),
                request.host(),
                request.port(),
                true,
                Instant.now()
        );
        log.info("Registering new node: {}", node);
        loadBalancerService.registerNode(node);

        log.info("Received heartbeat from service: {}", request.serviceId());
        return new HeartbeatResponse(true, "Heartbeat acknowledged", Instant.now().toEpochMilli());
    }

    @Scheduled(fixedRate = 10000) // Check every 10 seconds
    public void checkNodeHealth() {
        Instant threshold = Instant.now().minus(HEALTH_CHECK_TIMEOUT_SECONDS, ChronoUnit.SECONDS);

        loadBalancerService.getAllNodes().stream()
                .filter(node -> node.lastHeartbeat().isBefore(threshold))
                .forEach(node -> loadBalancerService.removeNode(node.serviceId()));
    }
}
