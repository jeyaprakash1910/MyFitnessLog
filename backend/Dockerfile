# Multi-stage build for the MyFitnessLog Spring Boot backend.
#
# Render dropped its native Java runtime, so the service now deploys as a Docker
# image (ADR-0015). Nothing about the application changes; this file only builds
# and runs the existing Maven project.
#
# Stage 1 builds the JAR from source with a full JDK + Maven (the project has no
# Maven Wrapper, so the build image supplies Maven). Stage 2 copies only that JAR
# into a slim JRE, leaving Maven, the compiler, and the source tree behind.

# --- Stage 1: build ---------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Resolve dependencies first so this layer is cached across source-only changes.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

# Build the JAR. Tests run in the normal dev/CI flow, not in the deploy image.
COPY src ./src
RUN mvn -B -DskipTests clean package

# --- Stage 2: runtime -------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Run as a non-root user rather than root.
RUN groupadd --system app && useradd --system --gid app app

COPY --from=build /build/target/*.jar app.jar
USER app

EXPOSE 8080

# Render injects PORT and expects the process to bind to it. Honour it via
# Spring's relaxed binding (--server.port) with an 8080 fallback for local runs.
# This needs no source or YAML change (ADR-0015).
ENTRYPOINT ["sh","-c","java ${JAVA_OPTS} -jar app.jar --server.port=${PORT:-8080}"]
