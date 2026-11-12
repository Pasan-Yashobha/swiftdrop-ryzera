# Identity Service image. Build context = this folder (services/identity).
# Based on templates/Dockerfile.service.

# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
# settings.xml redirects the Flyway GitHub Packages registry (requires auth) to
# Maven Central so the Docker builder never needs a GitHub token.
COPY .mvn/settings.xml .mvn/settings.xml
# Cache dependencies first so code-only changes rebuild fast
COPY pom.xml .
RUN mvn -B -q dependency:go-offline -s .mvn/settings.xml
COPY src ./src
RUN mvn -B -q package -DskipTests -s .mvn/settings.xml

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/target/identity-service.jar app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
