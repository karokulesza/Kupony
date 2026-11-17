#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
COMPOSE=(docker compose -f sonar/compose.yaml)
SONAR_URL="http://localhost:9000"
GATE="Kupony Strict"

cleanup() { "${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true; }
trap cleanup EXIT

echo "Starting SonarQube ..."
"${COMPOSE[@]}" up -d

echo "Waiting for SonarQube at ${SONAR_URL} ..."
status() { curl -sS "${SONAR_URL}/api/system/status" 2>/dev/null || true; }
for _ in $(seq 1 60); do
  [[ "$(status)" == *'"status":"UP"'* ]] && break
  sleep 5
done
[[ "$(status)" == *'"status":"UP"'* ]] || { echo "SonarQube did not start" >&2; exit 1; }

RANDOM_HEX=$(head -c 16 /dev/urandom | od -An -tx1 | tr -d ' \n')
PASSWORD="Kp1!${RANDOM_HEX}"
curl -sS -f -u admin:admin -X POST "${SONAR_URL}/api/users/change_password" \
  --data-urlencode "login=admin" --data-urlencode "previousPassword=admin" \
  --data-urlencode "password=${PASSWORD}" >/dev/null
SONAR_TOKEN=$(curl -sS -f -u "admin:${PASSWORD}" -X POST "${SONAR_URL}/api/user_tokens/generate" \
  --data-urlencode "name=analyze" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
[ -n "${SONAR_TOKEN}" ] || { echo "Could not create a SonarQube token" >&2; exit 1; }

api() {
  local method=$1 path=$2; shift 2
  curl -sS -f -u "${SONAR_TOKEN}:" -X "$method" "${SONAR_URL}/api/${path}" "$@"
}

echo "Creating quality gate '${GATE}':"
api POST "qualitygates/create" --data-urlencode "name=${GATE}" >/dev/null
add_condition() {
  api POST "qualitygates/create_condition" --data-urlencode "gateName=${GATE}" \
    --data-urlencode "metric=$1" --data-urlencode "op=$2" --data-urlencode "error=$3" >/dev/null
  echo "  $1 $2 $3 -> fail"
}
add_condition coverage                   LT 90
add_condition duplicated_lines_density   GT 1
add_condition sqale_rating               GT 1
add_condition reliability_rating         GT 1
add_condition security_rating            GT 1
add_condition bugs                       GT 0
add_condition vulnerabilities            GT 0
add_condition security_hotspots_reviewed LT 100
api POST "qualitygates/set_as_default" --data-urlencode "name=${GATE}" >/dev/null

./mvnw -B verify sonar:sonar -Dsonar.host.url="${SONAR_URL}" -Dsonar.token="${SONAR_TOKEN}" "$@"
