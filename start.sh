#!/bin/bash

# E-commerce Microservices Platform - Startup Script
# Usage: ./start.sh [options]
# Options:
#   -d, --detach    Run containers in detached mode (default)
#   -b, --build     Rebuild containers before starting
#   -f, --follow    Follow logs after startup
#   -h, --help      Show this help message

set -e

COMPOSE_FILE="docker-compose.dev.yml"
DETACH=true
BUILD=false
FOLLOW=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -d|--detach)
            DETACH=true
            shift
            ;;
        -b|--build)
            BUILD=true
            shift
            ;;
        -f|--follow)
            FOLLOW=true
            DETACH=true
            shift
            ;;
        -h|--help)
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  -d, --detach    Run containers in detached mode (default)"
            echo "  -b, --build     Rebuild containers before starting"
            echo "  -f, --follow    Follow logs after startup"
            echo "  -h, --help      Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use -h or --help for usage information"
            exit 1
            ;;
    esac
done

# Check if docker-compose is available
if ! command -v docker-compose &> /dev/null; then
    echo "Error: docker-compose is not installed"
    exit 1
fi

echo "============================================"
echo "  E-commerce Microservices Platform"
echo "============================================"
echo ""

# Build if requested
if [ "$BUILD" = true ]; then
    echo "Building containers..."
    docker-compose -f "$COMPOSE_FILE" build
    echo ""
fi

# Start services
echo "Starting services..."
if [ "$DETACH" = true ]; then
    docker-compose -f "$COMPOSE_FILE" up -d
    echo ""
    echo "Services started successfully!"
    echo ""
    echo "============================================"
    echo "  Service URLs"
    echo "============================================"
    echo ""
    printf "%-25s %s\n" "API Gateway:" "http://localhost:8765"
    printf "%-25s %s\n" "Eureka Dashboard:" "http://localhost:8761"
    printf "%-25s %s\n" "Config Server:" "http://localhost:8888"
    printf "%-25s %s\n" "Zipkin Tracing:" "http://localhost:9411"
    printf "%-25s %s\n" "PostgreSQL (Product):" "localhost:5432"
    printf "%-25s %s\n" "PostgreSQL (User):" "localhost:5433"
    printf "%-25s %s\n" "PostgreSQL (Payment):" "localhost:5434"
    printf "%-25s %s\n" "PostgreSQL (Order):" "localhost:5435"
    printf "%-25s %s\n" "Redis:" "localhost:6379"
    echo ""

    # Follow logs if requested
    if [ "$FOLLOW" = true ]; then
        echo "Following logs (Ctrl+C to stop)..."
        docker-compose -f "$COMPOSE_FILE" logs -f
    fi
else
    docker-compose -f "$COMPOSE_FILE" up
fi
