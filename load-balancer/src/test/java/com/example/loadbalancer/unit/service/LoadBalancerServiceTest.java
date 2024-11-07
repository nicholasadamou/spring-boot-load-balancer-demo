package com.example.loadbalancer.unit.service;

import com.example.loadbalancer.model.ServiceNode;
import com.example.loadbalancer.service.LoadBalancerService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LoadBalancerService Unit Tests")
@ExtendWith(MockitoExtension.class)
class LoadBalancerServiceTest {

    private LoadBalancerService loadBalancerService;

    @BeforeEach
    void setUp() {
        loadBalancerService = new LoadBalancerService();
    }

    @Nested
    @DisplayName("Node Registration Tests")
    class NodeRegistrationTests {

        @Test
        @DisplayName("Should successfully register a new node")
        void shouldRegisterNewNode() {
            // Given
            ServiceNode node = createTestNode();

            // When
            loadBalancerService.registerNode(node);

            // Then
            List<ServiceNode> nodes = loadBalancerService.getAllNodes();
            assertThat(nodes)
                    .hasSize(1)
                    .contains(node);
        }

        @Test
        @DisplayName("Should update existing node when registering with same ID")
        void shouldUpdateExistingNode() {
            // Given
            String serviceId = UUID.randomUUID().toString();
            ServiceNode originalNode = new ServiceNode(serviceId, "host1", 8081, true, Instant.now());
            ServiceNode updatedNode = new ServiceNode(serviceId, "host2", 8082, true, Instant.now());

            // When
            loadBalancerService.registerNode(originalNode);
            loadBalancerService.registerNode(updatedNode);

            // Then
            List<ServiceNode> nodes = loadBalancerService.getAllNodes();
            assertThat(nodes)
                    .hasSize(1)
                    .contains(updatedNode)
                    .doesNotContain(originalNode);
        }
    }

    @Nested
    @DisplayName("Node Removal Tests")
    class NodeRemovalTests {

        @Test
        @DisplayName("Should successfully remove an existing node")
        void shouldRemoveExistingNode() {
            // Given
            ServiceNode node = createTestNode();
            loadBalancerService.registerNode(node);

            // When
            loadBalancerService.removeNode(node.serviceId());

            // Then
            List<ServiceNode> nodes = loadBalancerService.getAllNodes();
            assertThat(nodes).isEmpty();
        }

        @Test
        @DisplayName("Should not throw exception when removing non-existent node")
        void shouldHandleRemovingNonExistentNode() {
            // When/Then
            assertDoesNotThrow(() -> loadBalancerService.removeNode("non-existent-id"));
        }
    }

    @Nested
    @DisplayName("Load Balancing Tests")
    class LoadBalancingTests {

        @Test
        @DisplayName("Should distribute requests in round-robin fashion")
        void shouldDistributeRequestsEvenly() {
            // Given
            int numNodes = 3;
            List<ServiceNode> nodes = IntStream.range(0, numNodes)
                    .mapToObj(i -> new ServiceNode(
                            UUID.randomUUID().toString(),
                            "host" + i,
                            8080 + i,
                            true,
                            Instant.now()
                    ))
                    .peek(loadBalancerService::registerNode)
                    .toList();

            // When
            int requestCount = numNodes * 3; // Make 3 complete cycles
            List<ServiceNode> selections = new ArrayList<>();
            for (int i = 0; i < requestCount; i++) {
                selections.add(loadBalancerService.getNextAvailableNode());
            }

            // Then
            // Verify each node appears equal number of times
            Map<String, Long> distributionCount = selections.stream()
                    .collect(Collectors.groupingBy(
                            ServiceNode::serviceId,
                            Collectors.counting()
                    ));

            assertThat(distributionCount).hasSize(numNodes);
            assertThat(distributionCount.values())
                    .allMatch(count -> count == requestCount / numNodes);

            // Verify round-robin pattern (each node appears once before repeating)
            for (int i = 0; i < selections.size() - numNodes; i++) {
                Set<String> roundNodes = selections.subList(i, i + numNodes)
                        .stream()
                        .map(ServiceNode::serviceId)
                        .collect(Collectors.toSet());
                assertThat(roundNodes).hasSize(numNodes);
            }
        }

        @Test
        @DisplayName("Should only select healthy nodes")
        void shouldOnlySelectHealthyNodes() {
            // Given
            ServiceNode healthyNode = new ServiceNode(
                    UUID.randomUUID().toString(),
                    "healthy-host",
                    8080,
                    true,
                    Instant.now()
            );
            ServiceNode unhealthyNode = new ServiceNode(
                    UUID.randomUUID().toString(),
                    "unhealthy-host",
                    8081,
                    false,
                    Instant.now()
            );

            loadBalancerService.registerNode(healthyNode);
            loadBalancerService.registerNode(unhealthyNode);

            // When/Then
            for (int i = 0; i < 5; i++) {
                ServiceNode selectedNode = loadBalancerService.getNextAvailableNode();
                assertThat(selectedNode).isEqualTo(healthyNode);
            }
        }

        @Test
        @DisplayName("Should throw exception when no healthy nodes available")
        void shouldThrowExceptionWhenNoHealthyNodes() {
            // Given
            ServiceNode unhealthyNode = new ServiceNode(
                    UUID.randomUUID().toString(),
                    "unhealthy-host",
                    8080,
                    false,
                    Instant.now()
            );
            loadBalancerService.registerNode(unhealthyNode);

            // When/Then
            assertThatThrownBy(() -> loadBalancerService.getNextAvailableNode())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("No healthy nodes available");
        }
    }

    private ServiceNode createTestNode() {
        return new ServiceNode(
                UUID.randomUUID().toString(),
                "test-host",
                8080,
                true,
                Instant.now()
        );
    }
}
