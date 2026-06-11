#!/bin/bash

set -e

echo "Removing all containers..."
docker rm -f $(docker ps -aq) 2>/dev/null || true

echo "Removing all custom networks..."
docker network ls --format '{{.Name}}' | grep -vE '^(bridge|host|none)$' | xargs -r docker network rm

echo "Removing all images..."
docker rmi -f $(docker images -aq) 2>/dev/null || true

echo "Removing all volumes..."
docker volume rm $(docker volume ls -q) 2>/dev/null || true

echo "Removing all build cache..."
docker builder prune -af

echo "Removing CloudCore data..."
rm -rf ~/.cloudcore/instances

echo "Done."