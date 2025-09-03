# ---- Build stage ----
FROM gradle:8.9-jdk17 AS builder

WORKDIR /home/gradle/src

# Copy everything
COPY --chown=gradle:gradle . .

# Build the app (skip tests for faster container builds)
RUN ./gradlew :api:shadowJar --no-daemon --stacktrace

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre

WORKDIR /api

# Copy the fat jar from builder stage
COPY --from=builder /home/gradle/src/api/build/libs/api-all.jar api.jar

# Expose the port Ktor runs on
EXPOSE 8080

# Run the application
ENTRYPOINT ["java","-jar","api.jar"]
