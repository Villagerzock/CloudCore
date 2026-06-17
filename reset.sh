#!/bin/bash

set -e

NETWORK="cloudcore"

echo "Removing containers in network: $NETWORK"
docker rm -f $(docker ps -aq --filter "network=$NETWORK") 2>/dev/null || true

echo "Removing network: $NETWORK"
docker network rm "$NETWORK" 2>/dev/null || true

echo "Removing CloudCore instances..."
rm -rf ~/.cloudcore/instances

echo "Done."