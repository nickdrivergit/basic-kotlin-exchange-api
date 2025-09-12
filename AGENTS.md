# Repository Guidelines

## Project Structure & Modules
- `api/` — Vert.x HTTP API (entrypoint `com.valr.api.ApplicationKt`).
  - `src/main/kotlin/` API routes, DTOs.
  - `src/test/kotlin/` API tests (JUnit 5 + Vert.x JUnit 5).
- `engine/` — order book domain and matching logic.
  - `src/main/kotlin/` core types (`com.valr.engine.*`).
  - `src/test/kotlin/` unit tests (JUnit 5).
  - `src/perfTest/kotlin/` performance tests (`:engine:perfTest`).
- `scripts/` — utilities (e.g., `spam-api-order.sh`).
- `Dockerfile` — multi-stage build using `:api:shadowJar`.

## Build, Test, and Run
- Build all: `./gradlew build`
- Run API locally: `./gradlew :api:run` then `curl http://localhost:8080/healthz`
- Fat JAR: `./gradlew :api:shadowJar` → `api/build/libs/api-all.jar`
- Unit tests: `./gradlew test` (or `:engine:test`, `:api:test`)
- Perf tests: `./gradlew :engine:perfTest`
- JMH benchmarks: `./gradlew :engine:jmh` (optional, produces reports under `engine/build/reports/jmh/`)
- Docker: `docker build -t exchange-api .` then `docker run -p 8080:8080 exchange-api`

## Coding Style & Naming
- Kotlin 1.9, JDK 17, Gradle Kotlin DSL.
- Follow Kotlin official style (4-space indent, 100–120 col soft limit).
- Packages: `com.valr.api` and `com.valr.engine`; match file and class names (PascalCase classes, lowerCamelCase members).
- Keep `engine` framework-free; API logic (routing/HTTP) lives in `api`.

## Testing Guidelines
- Framework: JUnit 5 across modules; Vert.x test utilities in `api`.
- Naming: test classes end with `Test` (e.g., `OrderBookMatchingTest.kt`).
- Run: `./gradlew test`; for perf: `./gradlew :engine:perfTest`; for microbenchmarks: `./gradlew :engine:jmh`.
- Prefer deterministic unit tests in `engine`; keep integration tests light in `api`.

## Commit & Pull Requests
- Commits: imperative mood, concise summary; prefix optional scope (e.g., `engine:` or `api:`). Example: `engine: fix level removal when empty`.
- PRs must include:
  - Purpose and scope, notable changes, and testing notes (commands run, evidence).
  - Linked issues (if any) and screenshots/cURL samples for API changes.
  - Passing CI and no new warnings; all tests green.

## Security & Configuration
- Default port `8080` (hardcoded). If changing, keep Docker, README, and scripts aligned.
- Validate all external inputs at API boundaries; keep `engine` validations intact.
