# ==============================================================================
# EasyEat Backend — Multi-stage Dockerfile
# Stage 1 (build): Maven 3.9 + Java 21 — compiles the jar.
# Stage 2 (run):   Eclipse Temurin 21 JRE Alpine — minimal runtime image.
# ==============================================================================

# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /app

# Copy the POM first to cache the dependency layer independently of source changes.
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build /app/target/easyeat-backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
