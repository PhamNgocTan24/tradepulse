# Stage 1: Extract Layers (Optimize caching for Spring Boot 3.2)
FROM eclipse-temurin:21-jre-alpine AS extractor
WORKDIR /app
ARG JAR_FILE=services/api-gateway-service/target/*.jar
COPY ${JAR_FILE} app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# Stage 3: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Security: Run application under non-root privileges
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy layers from the extractor stage in order of least to most frequently changed
COPY --from=extractor /app/dependencies/ ./
COPY --from=extractor /app/spring-boot-loader/ ./
COPY --from=extractor /app/snapshot-dependencies/ ./
COPY --from=extractor /app/application/ ./

EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
