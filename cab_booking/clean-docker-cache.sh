#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR" || exit

echo "Clearing Docker caches..."

# Clear local Maven cache
rm -rf ~/.m2/repository/* 2>/dev/null || true
echo "Cleared local Maven cache"

# Remove Docker build cache
echo "Removing Docker build cache..."
docker builder prune -f --filter type=build --filter unused=true 2>/dev/null || true

# Remove dangling images
docker image prune -f 2>/dev/null || true

# Rebuild auth-service with fresh cache
echo "Building auth-service with fresh cache..."
docker-compose -f auth-service/docker-compose.yaml build --no-cache auth-service
echo "Done! auth-service built successfully"

# Rebuild payment-service with fresh cache
echo "Building payment-service with fresh cache..."
docker-compose -f Payment-Service/docker-compose.yaml build --no-cache payment-service
echo "Done! payment-service built successfully"
