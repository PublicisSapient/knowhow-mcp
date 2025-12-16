########## STAGE 1: Build ##########
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom.xml first for caching dependencies
COPY pom.xml .
RUN mvn -q dependency:go-offline

# Copy source code
COPY src ./src

# Build the application (skip tests for faster build)
RUN mvn -q -DskipTests clean package

########## STAGE 2: Runtime ##########
FROM eclipse-temurin:17-jre

WORKDIR /app

# Install required system libraries for ONNX Runtime + curl
RUN apt-get update && apt-get install -y \
        libgomp1 \
        libstdc++6 \
        curl \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Create a non-root user safely
RUN useradd -m spring
USER spring

# Copy built JAR from builder stage
COPY --from=builder /app/target/*.jar /app/app.jar

# Expose application port
EXPOSE 8085

# Healthcheck using curl
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8085/mcp/api/actuator/health || exit 1

# Start the application
ENTRYPOINT ["java", "-jar", "app.jar"]