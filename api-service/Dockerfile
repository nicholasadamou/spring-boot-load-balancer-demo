FROM eclipse-temurin:21-jdk-alpine as builder
WORKDIR /build

# Install Maven
RUN apk add --no-cache maven

# First, copy all necessary files
COPY pom.xml .
COPY common/pom.xml common/
COPY common/src common/src
COPY load-balancer/pom.xml load-balancer/
COPY load-balancer/src load-balancer/src
COPY api-service/pom.xml api-service/
COPY api-service/src api-service/src

# Build all modules at once
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/api-service/target/*.jar app.jar
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
