#!/bin/bash

set -e

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${GREEN}===== zodiac_dewey dev start =====${NC}"

if [ -f .env ]; then
  export $(grep -v '^#' .env | xargs)
  echo "Loaded .env"
else
  echo -e "${RED}.env not found. Copy .env.example to .env first.${NC}"
  exit 1
fi

if [ -z "$AI_API_KEY" ]; then
  echo -e "${RED}AI_API_KEY is empty. The app will start, but report generation will fail until you fill it in.${NC}"
fi

cd backend
echo "Starting backend on http://localhost:${SERVER_PORT:-8080} (SQLite)"
mvn spring-boot:run
