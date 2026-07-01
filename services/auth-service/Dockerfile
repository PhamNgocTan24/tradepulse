# ==============================================================================
# TradePulse — auth-service Dockerfile
# Optimized for Java 21 & Kubernetes (AWS EKS)
# ==============================================================================

# Use Google's distroless image for maximum security and minimal size.
# Contains only JRE 21 and minimal OS library files (no shell, no package manager).
FROM gcr.io/distroless/java21-debian12:nonroot

WORKDIR /app

# JAR_FILE will be injected during the docker build phase from the Maven target directory
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar

# JVM options tailored for container environments:
# - G1GC: Optimized garbage collector for microservice workloads
# - MaxRAMPercentage=75.0: Dynamically respects cgroup memory limits (prevents OOM kills while allowing JVM to utilize up to 75% of container memory allocation)
# - UseStringDeduplication: Reduces memory footprint by deduplicating duplicate string instances
ENV JAVA_OPTS="-XX:+UseG1GC -XX:+UseStringDeduplication -XX:MaxRAMPercentage=75.0"

# Expose auth-service default port
EXPOSE 8081

# Run as nonroot user (pre-configured in distroless image)
USER nonroot

# Run the JAR directly (no shell is available in distroless to execute commands)
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:+UseStringDeduplication", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
