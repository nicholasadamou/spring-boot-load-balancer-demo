package com.example.api.service;

import org.springframework.stereotype.Service;
import java.net.InetAddress;

@Service
public class DemoService {
    public String getMessage() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            return "Hello from API Service running on " + hostname;
        } catch (Exception e) {
            return "Hello from API Service";
        }
    }
}
