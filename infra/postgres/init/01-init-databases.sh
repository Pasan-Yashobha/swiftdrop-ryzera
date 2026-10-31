#!/bin/bash
# Creates one database and one login role per microservice.
# Runs once, the first time the Postgres volume is created.
set -e

SERVICES="gateway identity merchant parcel pricing dispatch courier tracking notification webhook reporting audit document"

for svc in $SERVICES; do
  echo "Creating database ${svc}_db with owner ${svc}_svc"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE ROLE ${svc}_svc LOGIN PASSWORD '${SERVICE_DB_PASSWORD}';
    CREATE DATABASE ${svc}_db OWNER ${svc}_svc;
EOSQL
done
