#!/usr/bin/env bash
# Obtains a Keycloak access token for benchmark use.
# Usage:
#   export TOKEN=$(./performance/k6/scripts/get-token.sh)
#   k6 run -e TOKEN=$TOKEN performance/k6/02-jwt-auth.js

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
CLIENT_ID="${KEYCLOAK_CLIENT_ID:-sentinel-gateway-client}"
USERNAME="${KEYCLOAK_USERNAME:-alice}"
PASSWORD="${KEYCLOAK_PASSWORD:-alice-password}"
REALM="${KEYCLOAK_REALM:-sentinel}"

curl -s -X POST \
  "${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token" \
  -d "client_id=${CLIENT_ID}" \
  -d "grant_type=password" \
  -d "username=${USERNAME}" \
  -d "password=${PASSWORD}" \
  | jq -r '.access_token'
