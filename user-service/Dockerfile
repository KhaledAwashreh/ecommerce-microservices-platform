# Build stage
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source code and build
COPY . .
RUN mvn clean package

# Run stage
FROM eclipse-temurin:21-jre
WORKDIR /app

# Port is dynamically assigned (server.port=0 in application.properties)
COPY --from=builder /app/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]