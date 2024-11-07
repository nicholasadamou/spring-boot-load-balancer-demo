package com.example.loadbalancer.unit.service;

import com.example.common.dto.HeartbeatRequest;
import com.example.common.dto.HeartbeatResponse;
import com.example.loadbalancer.model.ServiceNode;
import com.example.loadbalancer.service.HealthCheckService;
import com.example.loadbalancer.service.LoadBalancerService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("HealthCheckService Unit Tests")
@ExtendWith(MockitoExtension.class)
class HealthCheckServiceTest {

    @Mock
    private LoadBalancerService loadBalancerService;

    private HealthCheckService healthCheckService;

    @BeforeEach
    void setUp() {
        healthCheckService = new HealthCheckService(loadBalancerService);
    }

    @Nested
    @DisplayName("Heartbeat Processing Tests")
    class HeartbeatProcessingTests {

        @Test
        @DisplayName("Should process valid heartbeat request")
        void shouldProcessValidHeartbeat() {
            // Given
            String serviceId = UUID.randomUUID().toString();
            HeartbeatRequest request = new HeartbeatRequest(
                    serviceId,
                    "test-host",
                    8080,
                    "UP",
                    Instant.now().toEpochMilli()
            );

            // When
            HeartbeatResponse response = healthCheckService.processHeartbeat(request);

            // Then
            verify(loadBalancerService).registerNode(any(ServiceNode.class));
            assertThat(response.acknowledged()).isTrue();
            assertThat(response.message()).isEqualTo("Heartbeat acknowledged");
        }

        @Test
        @DisplayName("Should handle multiple heartbeats from same service")
        void shouldHandleMultipleHeartbeats() {
            // Given
            String serviceId = UUID.randomUUID().toString();
            HeartbeatRequest firstRequest = new HeartbeatRequest(
                    serviceId,
                    "test-host",
                    8080,
                    "UP",
                    Instant.now().toEpochMilli()
            );
            HeartbeatRequest secondRequest = new HeartbeatRequest(
                    serviceId,
                    "test-host",
                    8080,
                    "UP",
                    Instant.now().toEpochMilli()
            );

            // When
            healthCheckService.processHeartbeat(firstRequest);
            healthCheckService.processHeartbeat(secondRequest);

            // Then
            verify(loadBalancerService, times(2)).registerNode(any(ServiceNode.class));
        }
    }

    @Nested
    @DisplayName("Health Check Tests")
    class HealthCheckTests {

        @Test
        @DisplayName("Should remove unhealthy nodes during check")
        void shouldRemoveUnhealthyNodes() {
            // Given
            Instant now = Instant.now();
            Instant oldTimestamp = now.minusSeconds(31); // Past the 30-second threshold

            ServiceNode unhealthyNode = new ServiceNode(
                    "unhealthy-id",
                    "unhealthy-host",
                    8080,
                    true,
                    oldTimestamp
            );

            when(loadBalancerService.getAllNodes()).thenReturn(List.of(unhealthyNode));

            // When
            healthCheckService.checkNodeHealth();

            // Then
            verify(loadBalancerService).removeNode("unhealthy-id");
        }

        @Test
        @DisplayName("Should keep healthy nodes during check")
        void shouldKeepHealthyNodes() {
            // Given
            Instant now = Instant.now();
            ServiceNode healthyNode = new ServiceNode(
                    "healthy-id",
                    "healthy-host",
                    8080,
                    true,
                    now
            );

            when(loadBalancerService.getAllNodes()).thenReturn(List.of(healthyNode));

            // When
            healthCheckService.checkNodeHealth();

            // Then
            verify(loadBalancerService, never()).removeNode(anyString());
        }
    }
}
