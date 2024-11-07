package com.example.common.dto;

public record HeartbeatRequest(
        String serviceId,
        String host,
        int port,
        String status,
        long timestamp
) {}