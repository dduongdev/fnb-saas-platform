#!/usr/bin/env bash
set -euo pipefail

# Simple deploy script for staging host. Customize per-environment.
# Assumptions:
# - Repo is cloned at /home/<user>/fnb-saas-platform
# - Docker and docker-compose plugin are installed and available
# - This script is run as the deployment user with sudo privileges when needed

REPO_DIR="/home/$(whoami)/fnb-saas-platform"
echo "Deploying in ${REPO_DIR}"

cd "${REPO_DIR}"

echo "Fetching latest code"
git fetch --all --prune
git checkout develop || true
git pull origin develop

TIMESTAMP=$(date +%Y%m%d-%H%M%S)
BACKUP_DIR="${REPO_DIR}/backups"
mkdir -p "${BACKUP_DIR}"

echo "[INFO] Pre-deploy: creating application-level backup placeholder"
# NOTE: implement DB-specific backup here (pg_dump/mysqldump or Cloud SQL export)
echo "backup-placeholder-${TIMESTAMP}" > "${BACKUP_DIR}/backup-${TIMESTAMP}.txt"

echo "Pulling docker images and starting services"
if [ -f docker-compose.yml ]; then
  sudo docker compose pull || true
  sudo docker compose up -d --build
else
  if [ -f backend/docker-compose.yml ]; then
    sudo docker compose -f backend/docker-compose.yml pull || true
    sudo docker compose -f backend/docker-compose.yml up -d --build
  else
    echo "No docker-compose.yml found; ensure deployment steps are implemented." >&2
    exit 3
  fi
fi

echo "Running migrations if present"
if [ -x ./backend/run_migrations.sh ]; then
  ./backend/run_migrations.sh
else
  echo "No migration script found at ./backend/run_migrations.sh — skip or implement migration steps"
fi

echo "Waiting for service health"
sleep 5
if command -v curl >/dev/null 2>&1; then
  HEALTHEP="http://localhost:8081/actuator/health"
  echo "Checking ${HEALTHEP}"
  curl -fsS ${HEALTHEP} || echo "Health check failed or endpoint not available"
fi

echo "Staging deploy finished"
