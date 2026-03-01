# ecommerce-microservices-platform

Production-grade e-commerce microservices architecture built with Spring Boot, Kubernetes, and AWS. Features independent Product, Order, and User services with Docker containerization, CI/CD pipelines, and comprehensive monitoring.

## Architecture Overview

This platform consists of the following microservices:

| Service | Port | Description |
|---------|------|-------------|
| **API Gateway** | 8765 | Entry point for all client requests, handles routing, load balancing, circuit breaker, and retry logic |
| **Naming Server (Eureka)** | 8761 | Service discovery registry for all microservices |
| **Config Server** | 8888 | Centralized configuration management with Git backend |
| **User Service** | Dynamic | Handles user management, authentication, and address data |
| **Product Service** | Dynamic | Manages products, categories, and reviews |
| **Order Service** | 8081 | Processes and manages customer orders |
| **Payment Service** | Dynamic | Handles payment processing |

## Technology Stack

- **Framework**: Spring Boot 3.x
- **Service Discovery**: Netflix Eureka
- **API Gateway**: Spring Cloud Gateway
- **Configuration**: Spring Cloud Config Server
- **Databases**: PostgreSQL (per-service databases)
- **Caching**: Redis
- **Distributed Tracing**: Zipkin
- **Containerization**: Docker

## Quick Start

### Prerequisites

- Docker and Docker Compose
- Maven 3.9+ (for local builds)
- Java 21

### Running with Docker Compose (Development)

```bash
# Start all services
docker-compose -f docker-compose.dev.yml up -d

# View logs
docker-compose -f docker-compose.dev.yml logs -f

# Stop all services
docker-compose -f docker-compose.dev.yml down
```

### Service URLs

| Service | URL |
|---------|-----|
| API Gateway | http://localhost:8765 |
| Eureka Dashboard | http://localhost:8761 |
| Config Server | http://localhost:8888 |
| Zipkin Tracing | http://localhost:9411 |

### API Endpoints

All services are accessible through the API Gateway:

```
GET  /api/v1/user/{id}         - Get user by ID
POST /api/v1/user              - Create new user
GET  /api/v1/product           - List all products
POST /api/v1/product           - Create new product
GET  /api/v1/orders             - List all orders
POST /api/v1/orders             - Create new order
```

## Docker Compose Services

### Infrastructure Services

| Service | Port | Description |
|---------|------|-------------|
| postgres-product | 5432 | Product service database |
| postgres-user | 5433 | User service database |
| postgres-payment | 5434 | Payment service database |
| postgres-order | 5435 | Order service database |
| redis | 6379 | Session/cache store |
| zipkin | 9411 | Distributed tracing |

### Environment Variables

Each microservice can be configured using the following environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Spring profile | local |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | Eureka server URL | http://localhost:8761/eureka |
| `ZIPKIN_BASE_URL` | Zipkin server URL | http://localhost:9411 |
| `SPRING_DATASOURCE_URL` | PostgreSQL connection URL | jdbc:postgresql://localhost:5432/dbname |
| `SPRING_DATASOURCE_USERNAME` | Database username | postgres |
| `SPRING_DATASOURCE_PASSWORD` | Database password | test1234 |
| `SPRING_DATA_REDIS_HOST` | Redis host | localhost |
| `SPRING_DATA_REDIS_PORT` | Redis port | 6379 |

## Development

### Building Services Locally

```bash
# Build all services
mvn clean install

# Build specific service
cd <service-directory>
mvn clean package
```

### Running Services Locally (Without Docker)

1. Start infrastructure services (PostgreSQL, Redis, Zipkin)
2. Start Naming Server first
3. Start Config Server
4. Start other microservices

## Health Checks

All services expose health endpoints at `/actuator/health`:

```bash
# Check service health
curl http://localhost:<port>/actuator/health
```

## Monitoring

### Zipkin Distributed Tracing

Access Zipkin UI at http://localhost:9411 to view:
- Service dependency graph
- Request traces
- Performance bottlenecks

### Eureka Service Registry

Access Eureka dashboard at http://localhost:8761 to:
- View registered services
- Monitor service health status
- See instance details

## License

MIT

## Inter-Service Communication

### Overview

The platform uses **synchronous HTTP communication** via Spring Cloud OpenFeign for inter-service calls. Each service exposes REST APIs consumed by other services through Feign clients.

### Service Communication Diagram

```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │
       ▼
┌─────────────────────────────────────────────┐
│            API Gateway (8765)                │
│  - Routing                                     │
│  - Circuit Breaker                            │
│  - Rate Limiting                              │
└──────────────────┬──────────────────────────────┘
                   │
       ┌───────────┼───────────┐
       ▼           ▼           ▼
┌──────────┐ ┌──────────┐ ┌────────────┐
│  Order   │ │  Product │ │   User    │
│ Service  │ │ Service  │ │  Service  │
└────┬─────┘ └──────────┘ └────────────┘
     │
     ├────────────────────┐
     ▼                     ▼
┌──────────┐        ┌───────────┐
│ Product  │        │  Payment  │
│ Service  │        │  Service  │
│ (stock)  │        │           │
└──────────┘        └───────────┘
```

### Feign Clients

| Client | Service Called | Purpose |
|--------|---------------|---------|
| `ProductServiceClient` | Product Service | Retrieve products, check inventory |
| `PaymentClient` | Payment Service | Process payments |

### Circuit Breaker & Retry

Resilience4j is configured for fault tolerance:

- **Circuit Breaker**: Opens after 50% failure rate in 10 calls
- **Retry**: 3 attempts with exponential backoff
- Applied to all Feign client calls

---

## Order Processing Flow

### Sequence Diagram

```
┌────────┐    ┌─────────┐   ┌─────────┐   ┌──────────┐   ┌─────────┐
│ Client │    │ Order   │   │Product  │   │Inventory│   │Payment  │
│        │    │ Service │   │ Service │   │ Table   │   │ Service │
└───┬────┘    └────┬────┘   └────┬────┘   └────┬────┘   └────┬────┘
    │              │            │            │            │
    │ POST /order │            │            │            │
    │────────────>│            │            │            │
    │              │            │            │            │
    │              │ GET /product/{id} │            │
    │              │───────────>│            │            │
    │              │<───────────│            │            │
    │              │            │            │            │
    │              │ GET /inventory/{variationId} │
    │              │───────────────────────────>│            │
    │              │<──────────────────────────│            │
    │              │            │            │            │
    │   Check stock availability            │            │
    │              │            │            │            │
    │              │ PUT /inventory/deduct   │
    │              │─────────────────────────>│            │
    │              │<────────────────────────│            │
    │              │            │            │            │
    │              │ POST /payment/process   │            │
    │              │────────────────────────────────────>│
    │              │<────────────────────────────│            │
    │              │            │            │            │
    │  CONFIRMED  │            │            │            │
    │<────────────│            │            │            │
    │              │            │            │            │
```

### Order States

| State | Description |
|-------|-------------|
| `PENDING` | Order created, awaiting inventory validation |
| `CONFIRMED` | Payment successful, inventory deducted |
| `CANCELLED` | Payment failed or inventory unavailable |

### Error Handling

1. **Insufficient Stock**: Returns `InsufficientStockException`
2. **Service Unavailable**: Circuit breaker opens, returns fallback
3. **Payment Failed**: Inventory is restored (compensating transaction)

---

## Database Schema

### Inventory Table (Product Service)


```sql
CREATE TABLE inventory (
    id UUID PRIMARY KEY,
    product_variation_id UUID NOT NULL,
    quantity INT NOT NULL DEFAULT 0,
    reserved_quantity INT NOT NULL DEFAULT 0,
    warehouse_location VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

### Payment Table (Payment Service)

```sql
CREATE TABLE payment (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    buyer_id UUID NOT NULL,
    amount DECIMAL NOT NULL,
    payment_method VARCHAR(50),
    status VARCHAR(20) NOT NULL,
    transaction_id VARCHAR(255),
    payment_gateway VARCHAR(50),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

MIT
