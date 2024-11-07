package com.example.loadbalancer.service;

import com.example.loadbalancer.model.ServiceNode;
import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.ArrayList;

@Service
public class LoadBalancerService {
    private final ConcurrentHashMap<String, ServiceNode> serviceNodes = new ConcurrentHashMap<>();
    private final AtomicInteger currentNodeIndex = new AtomicInteger(0);

    public void registerNode(ServiceNode node) {
        serviceNodes.put(node.serviceId(), node);
    }

    public void removeNode(String serviceId) {
        serviceNodes.remove(serviceId);
    }

    public ServiceNode getNextAvailableNode() {
        List<ServiceNode> healthyNodes = serviceNodes.values().stream()
                .filter(ServiceNode::healthy)
                .toList();

        if (healthyNodes.isEmpty()) {
            throw new IllegalStateException("No healthy nodes available");
        }

        int index = currentNodeIndex.getAndIncrement() % healthyNodes.size();
        return healthyNodes.get(index);
    }

    public List<ServiceNode> getAllNodes() {
        return new ArrayList<>(serviceNodes.values());
    }
}
