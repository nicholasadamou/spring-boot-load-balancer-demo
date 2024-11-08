# Spring Boot Load Balancer Demo
![Spring](https://img.shields.io/badge/-Spring-6DB33F?style=flat-square&logo=spring&logoColor=white)
![Docker](https://img.shields.io/badge/-Docker-2496ED?style=flat-square&logo=Docker&logoColor=white)
![Linux](https://img.shields.io/badge/-Linux-FCC624?style=flat-square&logo=linux&logoColor=black)

A demonstration of a simple but functional load balancer implementation using Spring Boot, showcasing service discovery, health checking, and round-robin load distribution.

## Architecture

The system consists of three main components:

```mermaid
graph TB
    %% Styling
    classDef clientStyle fill:#f9f9f9,stroke:#666,stroke-width:2px
    classDef loadBalancerStyle fill:#e6f3ff,stroke:#3385ff,stroke-width:2px
    classDef serviceStyle fill:#f0fff0,stroke:#33cc33,stroke-width:2px
    classDef componentStyle fill:#fff,stroke:#999,stroke-width:1px

    %% Client Layer
    subgraph Clients["Client Layer"]
        direction LR
        C1[Web Client]
        C2[Mobile Client]
        C3[API Client]
    end

    %% Load Balancer Layer
    subgraph LoadBalancer["Load Balancer Layer"]
        direction TB
        PC[Proxy Controller]
        LBS[Load Balancer<br>Service]
        HCS[Health Check<br>Service]

        PC --> LBS
        LBS --> HCS
    end

    %% API Services Layer
    subgraph Services["API Service Cluster"]
        direction LR
        API1[API Service<br>:8081]
        API2[API Service<br>:8082]
        API3[API Service<br>:8083]
        API4[API Service<br>:8084]
        API5[API Service<br>:8085]
    end

    %% Connections
    C1 --> |HTTP Requests| PC
    C2 --> |HTTP Requests| PC
    C3 --> |HTTP Requests| PC

    PC --> |Round Robin| API1
    PC --> |Round Robin| API2
    PC --> |Round Robin| API3
    PC --> |Round Robin| API4
    PC --> |Round Robin| API5

    API1 --> |Heartbeat 5s| HCS
    API2 --> |Heartbeat 5s| HCS
    API3 --> |Heartbeat 5s| HCS
    API4 --> |Heartbeat 5s| HCS
    API5 --> |Heartbeat 5s| HCS
```

## Features

- Round-robin load balancing
- Dynamic service registration
- Automatic health checking
- Request forwarding for all HTTP methods
- Docker support
- Spring Boot Actuator integration
- Modular architecture

## Tech Stack

- Java 21
- Spring Boot 3.2.0
- Docker & Docker Compose
- Maven
- Project Lombok
- Spring Boot Actuator

## Project Structure

```
.
├── api-service/             # API service module
├── common/                  # Shared DTOs and utilities
├── load-balancer/           # Load balancer core module
├── docker-compose.yml       # Docker composition
└── pom.xml                  # Parent POM file
```

### Modules

1. **load-balancer**: Core load balancing functionality
    - Load balancing service
    - Health checking
    - Request proxying

2. **api-service**: Demo service for testing
    - Simple REST endpoints
    - Health reporting
    - Multiple instance support

3. **common**: Shared components
    - DTOs
    - Exceptions
    - Common utilities

## Getting Started

### Prerequisites

- Java 21 JDK
- Docker and Docker Compose
- Maven 3.9+

### Building the Project

```bash
# Clone the repository
git clone [repository-url]

# Build with Maven
./mvnw clean package

# Build with Docker Compose
docker-compose build
```

### Running the Application

```bash
# Start all services using Docker Compose
docker-compose up

# Or start individual components with Maven
./mvnw spring-boot:run -pl load-balancer
./mvnw spring-boot:run -pl api-service
```

### Configuration

#### Load Balancer (application.yml)
```yaml
server:
  port: 8080

spring:
  application:
    name: load-balancer

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

#### API Service (application.yml)
```yaml
server:
  port: ${SERVER_PORT:8081}

loadbalancer:
  url: ${LOADBALANCER_URL:http://localhost:8080}
```

## Testing

### Manual Testing

1. Start the services:
```bash
docker-compose up
```

2. Send requests to the load balancer:
```bash
curl http://localhost:8080/api/demo
```

3. Monitor distribution of requests across services in the logs.

### Health Check Testing

1. Monitor service health:
```bash
curl http://localhost:8080/actuator/health
```

2. View registered services:
```bash
curl http://localhost:8080/actuator/metrics
```

## Docker Support

The project includes Dockerfile for each service and a docker-compose.yml for orchestration. To run with Docker:

```bash
# Build and start all services
docker-compose up --build

# Scale API services
docker-compose up --scale api-service=5

# Stop all services
docker-compose down
```

## Monitoring

The application exposes several endpoints through Spring Boot Actuator:

- `/actuator/health`: Health information
- `/actuator/info`: Application information
- `/actuator/metrics`: Metrics data

## Implementation Details

### Load Balancing Algorithm

The load balancer uses a round-robin algorithm implemented with an AtomicInteger:

```java
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
```

### Health Checking

Services send heartbeats every 5 seconds, and nodes are considered unhealthy after 30 seconds of no heartbeat:

```java
@Scheduled(fixedRate = 10000) // Check every 10 seconds
public void checkNodeHealth() {
    Instant threshold = Instant.now().minus(HEALTH_CHECK_TIMEOUT_SECONDS,
            ChronoUnit.SECONDS);

    loadBalancerService.getAllNodes().stream()
            .filter(node -> node.lastHeartbeat().isBefore(threshold))
            .forEach(node -> loadBalancerService.removeNode(node.serviceId()));
}
```
