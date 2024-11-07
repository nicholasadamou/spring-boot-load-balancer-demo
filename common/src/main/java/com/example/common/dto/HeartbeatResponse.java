package com.example.common.dto;

public record HeartbeatResponse(
        boolean acknowledged,
        String message,
        long timestamp
) {}
