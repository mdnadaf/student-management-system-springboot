# Build stage
FROM maven:3.9.9-eclipse-temurin-23 AS build
WORKDIR /app

# Copy dependency definition & wrappers
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
COPY mvnw.cmd .

# Copy source code and package
COPY src ./src
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:23-jre
WORKDIR /app
COPY --from=build /app/target/studentmanagement-0.0.1-SNAPSHOT.jar app.jar

ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
