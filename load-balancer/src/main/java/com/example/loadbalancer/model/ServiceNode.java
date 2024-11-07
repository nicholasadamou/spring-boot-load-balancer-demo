package com.example.loadbalancer.model;

import java.time.Instant;

public record ServiceNode(
        String serviceId,
        String host,
        int port,
        boolean healthy,
        Instant lastHeartbeat
) {}
