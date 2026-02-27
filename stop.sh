#!/bin/bash

# E-commerce Microservices Platform - Stop Script
# Usage: ./stop.sh [options]
# Options:
#   -v, --volumes   Remove volumes as well (data will be lost)
#   -h, --help      Show this help message

set -e

COMPOSE_FILE="docker-compose.dev.yml"
REMOVE_VOLUMES=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -v|--volumes)
            REMOVE_VOLUMES=true
            shift
            ;;
        -h|--help)
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  -v, --volumes   Remove volumes as well (data will be lost)"
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

echo "Stopping services..."

if [ "$REMOVE_VOLUMES" = true ]; then
    echo "WARNING: Removing volumes - all data will be lost!"
    docker-compose -f "$COMPOSE_FILE" down -v
else
    docker-compose -f "$COMPOSE_FILE" down
fi

echo ""
echo "Services stopped."
