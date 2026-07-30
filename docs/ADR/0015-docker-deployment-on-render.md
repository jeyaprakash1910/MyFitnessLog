# ADR-0015 — The backend deploys to Render as a Docker image

Date: 2026-07-30
Status: Accepted
Related: ADR-0014 (dual-pooler datasource), ADR-0013 (API-key auth boundary),
backend/Dockerfile, backend/.dockerignore, docs/development/DEPLOYMENT.md

## Context

Production runs the Spring Boot backend on Render. The original deployment plan
used Render's **native Java runtime**: Render would install JDK 21, build with
Maven, and start the packaged JAR.

During Render service creation this runtime was found to be gone. New Web
Services now offer only Docker, Node, Python, Go, Ruby, Rust, and Elixir — there
is no Java option. The native-Java plan is therefore no longer possible, and a
new deployment mechanism is required. Nothing about the application itself (code,
Spring configuration, schema, APIs) needs to change — this is purely an
infrastructure-compatibility decision.

## Decision

Deploy the backend to Render as a **Docker image built from a multi-stage
`backend/Dockerfile`**, built by Render from source on each push:

- **Build stage** `maven:3.9-eclipse-temurin-21` compiles and packages the JAR
  with `mvn -B -DskipTests clean package`. The project has no Maven Wrapper, so
  the build image supplies Maven; no `mvnw` is added.
- **Runtime stage** `eclipse-temurin:21-jre` runs only the resulting JAR, as a
  non-root user. Maven, the compiler, and the source tree are left behind.
- The image is built **internally from source** (Render clones the repo), not
  from a locally produced JAR.
- Render injects a `PORT` env var; the entrypoint honours it via Spring's
  relaxed binding (`--server.port=${PORT:-8080}`), so **no source or YAML change
  is needed**.
- `backend/.dockerignore` keeps `target/`, local env files, and VCS/IDE metadata
  out of the build context.

The container inherits the production runtime configuration unchanged: the
dual-pooler datasource (ADR-0014) — runtime on the transaction pooler (`:6543`),
Flyway on the session pooler (`:5432`) — and the API-key auth boundary (ADR-0013)
both come from environment variables, so containerisation neither alters nor
re-implements them.

Both base images are pinned to Java 21 (matching `pom.xml`) and are multi-arch,
so the same Dockerfile builds on Apple Silicon locally and on Render's amd64.

## Alternatives considered

**Native Java runtime on Render.** The original plan. Rejected because Render no
longer offers it — not a preference, an availability change.

**Local Maven build, then copy the JAR into a runtime-only image.** Simpler
Dockerfile, but requires a local build step before every deploy and makes the
image depend on developer-machine state. Rejected: Render builds from source, so
there is no local JAR to copy, and a single-stage build from source is more
reproducible.

**Single-stage build image as the runtime.** Ships Maven, the JDK, and the full
source in the running image for no benefit. Rejected in favour of the multi-stage
split (JRE-only runtime, smaller and with less surface).

**Alpine (`-alpine`) base images.** Smaller, but musl libc can surprise JNI/Netty
paths. Rejected: the size saving does not justify the risk for one service.

**Changing `server.port` in application.yml to read `PORT`.** Would work but is
an application-config change for an infrastructure concern. Rejected in favour of
the entrypoint flag, keeping the port mapping entirely inside the Dockerfile.

## Consequences

Positive:

- The runtime is version-pinned in-repo rather than depending on a
  platform-provided JDK; the same image runs on Render, locally, or any other
  container host.
- The image can be built and smoke-tested locally (`docker build` / `docker run`)
  before pushing, catching build issues off the deploy path.
- No change to application behaviour, configuration, schema, or APIs.

Negative / accepted:

- Render's build/start-command fields no longer apply; the image's ENTRYPOINT
  starts the app. The deployment guide is updated to reflect the Docker runtime.
- Docker image builds are slower than a bare Maven build on the first push
  (dependency layer), though dependency caching mitigates subsequent builds.
- Two base-image tags must be kept in step with the project's Java version if it
  ever moves off 21.
