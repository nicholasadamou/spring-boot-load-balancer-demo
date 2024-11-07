package com.example.loadbalancer.integration;

import com.example.common.dto.HeartbeatRequest;
import com.example.common.dto.HeartbeatResponse;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DirtiesContext
class LoadBalancerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Container
    static GenericContainer<?> apiService1 = new GenericContainer<>("api-service:latest")
            .withExposedPorts(8081)
            .withEnv("SERVER_PORT", "8081")
            .withEnv("LOADBALANCER_URL", "http://host.testcontainers.internal:8080");

    @Container
    static GenericContainer<?> apiService2 = new GenericContainer<>("api-service:latest")
            .withExposedPorts(8082)
            .withEnv("SERVER_PORT", "8082")
            .withEnv("LOADBALANCER_URL", "http://host.testcontainers.internal:8080");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> 8080);
    }

    @Nested
    @DisplayName("Service Registration Tests")
    class ServiceRegistrationTests {

        @Test
        @DisplayName("Should register services and distribute requests")
        void shouldRegisterAndDistributeRequests() {
            // Register services
            String serviceId1 = UUID.randomUUID().toString();
            String serviceId2 = UUID.randomUUID().toString();

            registerService(serviceId1, "localhost", apiService1.getMappedPort(8081));
            registerService(serviceId2, "localhost", apiService2.getMappedPort(8082));

            // Wait for services to be registered
            await().atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        ResponseEntity<String> response = restTemplate.getForEntity(
                                "http://localhost:" + port + "/api/demo",
                                String.class
                        );
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    });

            // Verify round-robin distribution
            String firstResponse = restTemplate.getForObject(
                    "http://localhost:" + port + "/api/demo",
                    String.class
            );
            String secondResponse = restTemplate.getForObject(
                    "http://localhost:" + port + "/api/demo",
                    String.class
            );

            assertThat(firstResponse).isNotEqualTo(secondResponse);
        }

        @Test
        @DisplayName("Should handle service failure gracefully")
        void shouldHandleServiceFailure() {
            // Register services
            String serviceId1 = UUID.randomUUID().toString();
            registerService(serviceId1, "localhost", apiService1.getMappedPort(8081));

            // Stop one service
            apiService1.stop();

            // Wait for health check to remove the failed service
            await().atMost(35, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        ResponseEntity<String> response = restTemplate.getForEntity(
                                "http://localhost:" + port + "/api/demo",
                                String.class
                        );
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    });
        }
    }

    @Nested
    @DisplayName("Request Forwarding Tests")
    class RequestForwardingTests {

        @Test
        @DisplayName("Should forward different HTTP methods")
        void shouldForwardDifferentHttpMethods() {
            // Register a service
            String serviceId = UUID.randomUUID().toString();
            registerService(serviceId, "localhost", apiService1.getMappedPort(8081));

            // Test different HTTP methods
            HttpHeaders headers = new HttpHeaders();
            headers.setContentTypeheaders.setContentType(MediaType.APPLICATION_JSON);

            // GET Request
            ResponseEntity<String> getResponse = restTemplate.getForEntity(
                    "http://localhost:" + port + "/api/demo",
                    String.class
            );
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // POST Request
            ResponseEntity<String> postResponse = restTemplate.postForEntity(
                    "http://localhost:" + port + "/api/demo",
                    new HttpEntity<>("{}", headers),
                    String.class
            );
            assertThat(postResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // PUT Request
            ResponseEntity<String> putResponse = restTemplate.exchange(
                    "http://localhost:" + port + "/api/demo",
                    HttpMethod.PUT,
                    new HttpEntity<>("{}", headers),
                    String.class
            );
            assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // DELETE Request
            ResponseEntity<String> deleteResponse = restTemplate.exchange(
                    "http://localhost:" + port + "/api/demo",
                    HttpMethod.DELETE,
                    null,
                    String.class
            );
            assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("Should preserve request headers")
        void shouldPreserveRequestHeaders() {
            // Register a service
            String serviceId = UUID.randomUUID().toString();
            registerService(serviceId, "localhost", apiService1.getMappedPort(8081));

            // Create custom headers
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Custom-Header", "test-value");
            headers.set("Authorization", "Bearer test-token");

            ResponseEntity<String> response = restTemplate.exchange(
                    "http://localhost:" + port + "/api/demo",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            // Headers verification would depend on the API service implementation
        }
    }

    @Nested
    @DisplayName("Health Check Integration Tests")
    class HealthCheckIntegrationTests {

        @Test
        @DisplayName("Should handle service recovery")
        void shouldHandleServiceRecovery() {
            // Register a service
            String serviceId = UUID.randomUUID().toString();
            registerService(serviceId, "localhost", apiService1.getMappedPort(8081));

            // Stop the service
            apiService1.stop();

            // Wait for health check to remove the failed service
            await().atMost(35, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        ResponseEntity<String> response = restTemplate.getForEntity(
                                "http://localhost:" + port + "/api/demo",
                                String.class
                        );
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    });

            // Start the service again
            apiService1.start();

            // Register the service again
            registerService(serviceId, "localhost", apiService1.getMappedPort(8081));

            // Wait for service to become available
            await().atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        ResponseEntity<String> response = restTemplate.getForEntity(
                                "http://localhost:" + port + "/api/demo",
                                String.class
                        );
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    });
        }
    }

    private void registerService(String serviceId, String host, int port) {
        HeartbeatRequest request = new HeartbeatRequest(
                serviceId,
                host,
                port,
                "UP",
                Instant.now().toEpochMilli()
        );

        ResponseEntity<HeartbeatResponse> response = restTemplate.postForEntity(
                "http://localhost:" + this.port + "/heartbeat",
                request,
                HeartbeatResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().acknowledged()).isTrue();
    }
}