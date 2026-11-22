# SwiftDrop: Parcel Service

Member 04 responsibility in the SwiftDrop Enterprise Application Development group project.

## Overview
The **Parcel Service** handles parcel bookings, parcel lifecycle state transitions, persistent status history, customer delivery tracking, and event emission.

## Tech Stack
- **Language**: Java 21
- **Framework**: Spring Boot 3.5.16
- **Database**: PostgreSQL (runtime: `parcel_db`, owner: `parcel_svc`), Flyway 10.21.0
- **Security**: Spring Security + OAuth2 Resource Server (Bearer JWT issued by Identity Service) + `X-Internal-Token` filter for internal service-to-service communication
- **Messaging**: RabbitMQ (Spring AMQP), Transactional Outbox pattern on topic exchange `swiftdrop.events`
- **Observability**: Spring Boot Actuator, Micrometer Prometheus metrics
- **Build & Quality**: Maven, JaCoCo (minimum 70% line coverage enforced on `mvn verify`)

## Service Ports
- **Internal / Container Port**: `8080`
- **Local Dev Port**: `8084`

## Building and Testing
```bash
# Compile and package
mvn clean package

# Run tests and verify JaCoCo coverage
mvn clean verify
```
