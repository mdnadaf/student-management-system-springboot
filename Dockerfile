# Build stage
FROM maven:3.9-eclipse-temurin-23 AS build
WORKDIR /app

# Cache Maven dependencies before copying source code
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Copy source code and build executable JAR
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# Run stage
FROM eclipse-temurin:23-jre
WORKDIR /app

# Create a non-root user and group
RUN groupadd --system appgroup && useradd --system --gid appgroup --no-create-home appuser

# Copy the Spring Boot executable JAR from the build stage
COPY --from=build --chown=appuser:appgroup /app/target/*.jar app.jar

USER appuser

# Tune JVM memory for Render free tier (512 MB RAM)
ENV JAVA_TOOL_OPTIONS="-Xmx400m -XX:+UseSerialGC"

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
