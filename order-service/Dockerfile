# Build stage
FROM  maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Copy pom.xml and download dependencies
# Copy root pom first
COPY pom.xml .

# Copy every child module's pom.xml so Maven can resolve the full module tree
COPY api-gateway/pom.xml      api-gateway/pom.xml
COPY user-service/pom.xml     user-service/pom.xml
COPY product-service/pom.xml  product-service/pom.xml
COPY order-service/pom.xml    order-service/pom.xml
COPY payment-service/pom.xml  payment-service/pom.xml
COPY frontend-service/pom.xml frontend-service/pom.xml

# Now Maven can see the full module structure — dependencies download correctly
RUN mvn dependency:go-offline

# Copy the rest of the source code
COPY . .

# Build only this specific module, not the entire reactor
RUN mvn clean package -DskipTests -pl order-service -am

# Run stage
FROM eclipse-temurin:21-jre
WORKDIR /app

# Install curl for health check
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Port is dynamically assigned (server.port=0 in application.properties)
COPY --from=builder /app/target/*.jar app.jar

ENV MANAGEMENT_SERVER_PORT=8081

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8081/actuator/health || exit 1

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]