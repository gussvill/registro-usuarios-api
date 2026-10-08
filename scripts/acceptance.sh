#!/usr/bin/env bash
#
# Acceptance checks for a running instance of the user registration API.
#
#   scripts/acceptance.sh                              # http://localhost:8080
#   BASE_URL=http://localhost:9090 scripts/acceptance.sh
#
# Needs only bash and curl. Run it against a FRESH instance: the database is in memory, and the
# statement's example address can be registered only once. The script stops at the first failed
# check, prints a summary and exits with a non-zero status.

set -u

BASE_URL="${BASE_URL:-http://localhost:8080}"
WAIT_SECONDS="${WAIT_SECONDS:-30}"

STATEMENT_BODY='{"name":"Juan Rodriguez","email":"juan@rodriguez.org","password":"hunter2","phones":[{"number":"1234567","citycode":"1","contrycode":"57"}]}'

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

PASSED=0
STATUS=""
BODY=""
HEADERS=""

# --- helpers ---------------------------------------------------------------------------------

summary() {
  echo
  echo "Summary: ${PASSED} check(s) passed against ${BASE_URL}"
}

fail() {
  echo "  FAIL  $1"
  echo "        status:  ${STATUS}"
  echo "        headers: $(printf '%s' "$HEADERS" | tr '\r\n' '  ')"
  echo "        body:    ${BODY}"
  summary
  echo "Result: FAILED"
  exit 1
}

pass() {
  PASSED=$((PASSED + 1))
  echo "  ok    $1"
}

# request <curl arguments...>: stores the status, the headers and the body of one call.
request() {
  STATUS="$(curl -s -o "$WORK_DIR/body" -D "$WORK_DIR/headers" -w '%{http_code}' "$@")"
  BODY="$(cat "$WORK_DIR/body")"
  HEADERS="$(cat "$WORK_DIR/headers")"
}

post_json() {
  request -X POST "${BASE_URL}/api/v1/users" -H 'Content-Type: application/json' "$@"
}

expect_status() { # <expected> <label>
  [ "$STATUS" = "$1" ] || fail "$2: expected status $1"
}

expect_header_starts_with() { # <header> <prefix> <label>
  local value
  value="$(printf '%s' "$HEADERS" | tr -d '\r' | grep -i "^$1:" | head -n 1 | cut -d: -f2- | sed 's/^ *//')"
  case "$value" in
    "$2"*) ;;
    *) fail "$3: header $1 is '${value}', expected it to start with '$2'" ;;
  esac
}

expect_body() { # <exact body> <label>
  [ "$BODY" = "$1" ] || fail "$2: expected body $1"
}

expect_body_contains() { # <fragment> <label>
  case "$BODY" in
    *"$1"*) ;;
    *) fail "$2: body does not contain $1" ;;
  esac
}

expect_body_lacks() { # <fragment> <label>
  case "$BODY" in
    *"$1"*) fail "$2: body must not contain $1" ;;
    *) ;;
  esac
}

# A body that is exactly one {"mensaje": "..."} object.
expect_mensaje_only() { # <label>
  printf '%s' "$BODY" | grep -Eq '^\{"mensaje":"[^"]+"\}$' || fail "$1: body is not a single mensaje object"
}

# --- wait for the application ------------------------------------------------------------------

echo "Acceptance checks against ${BASE_URL}"
waited=0
until curl -s -o /dev/null "${BASE_URL}/v3/api-docs"; do
  if [ "$waited" -ge "$WAIT_SECONDS" ]; then
    echo "The application did not answer at ${BASE_URL} within ${WAIT_SECONDS} seconds."
    echo "Result: FAILED"
    exit 1
  fi
  sleep 1
  waited=$((waited + 1))
done

# --- registration ------------------------------------------------------------------------------

echo
echo "Registration"

post_json -d "$STATEMENT_BODY"
if [ "$STATUS" = "409" ]; then
  echo "  The statement's address is already registered: run this script against a fresh instance."
fi
expect_status 201 "statement body"
expect_header_starts_with content-type application/json "statement body"
for key in id name email phones created modified last_login token isactive; do
  expect_body_contains "\"${key}\":" "statement body, key ${key}"
done
expect_body_contains '"isactive":true' "statement body"
expect_body_contains '"contrycode":"57"' "statement body"
expect_body_lacks "password" "statement body"
expect_body_lacks "hunter2" "statement body"
printf '%s' "$BODY" | grep -Eq '"token":"[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+"' \
  || fail "statement body: token is not a three-part JWT"
pass "statement body -> 201 with id, created, modified, last_login, token, isactive and no password"

post_json -d "$STATEMENT_BODY"
expect_status 409 "repeated registration"
expect_body '{"mensaje":"El correo ya registrado"}' "repeated registration"
pass "repeated registration -> 409 {\"mensaje\":\"El correo ya registrado\"}"

# --- hostile and invalid input -------------------------------------------------------------------

echo
echo "Invalid input"

post_json -d '{"name":"","email":"bad","password":"x"}'
expect_status 400 "invalid fields"
expect_mensaje_only "invalid fields"
pass "invalid fields -> 400 with a single mensaje"

post_json -d '{"name":'
expect_status 400 "malformed JSON"
expect_body '{"mensaje":"El cuerpo de la solicitud no es válido"}' "malformed JSON"
pass "malformed JSON -> 400"

# --- errors the framework produces ------------------------------------------------------------------

echo
echo "Framework errors"

request -X GET "${BASE_URL}/api/v1/users"
expect_status 405 "GET on the registration route"
expect_header_starts_with allow POST "GET on the registration route"
expect_mensaje_only "GET on the registration route"
pass "GET /api/v1/users -> 405 with Allow: POST"

request -X GET "${BASE_URL}/nope"
expect_status 404 "unknown route"
expect_mensaje_only "unknown route"
pass "GET /nope -> 404"

request -X POST "${BASE_URL}/api/v1/users" -H 'Content-Type: text/plain' -d 'plain text'
expect_status 415 "text/plain body"
expect_mensaje_only "text/plain body"
pass "POST text/plain -> 415"

request -X POST "${BASE_URL}/api/v1/users" -H 'Content-Type: application/json' -H 'Accept: application/xml' \
  -d '{"name":"Ana Perez","email":"ana@dominio.cl","password":"hunter2","phones":[]}'
expect_status 406 "Accept application/xml"
expect_header_starts_with content-type application/json "Accept application/xml"
expect_mensaje_only "Accept application/xml"
pass "Accept: application/xml -> 406 with a JSON body"

# --- documentation endpoints ---------------------------------------------------------------------------

echo
echo "Documentation"

request -L -X GET "${BASE_URL}/swagger-ui.html"
expect_status 200 "Swagger UI"
pass "GET /swagger-ui.html -> 200"

request -X GET "${BASE_URL}/v3/api-docs"
expect_status 200 "OpenAPI document"
expect_body_contains '"openapi"' "OpenAPI document"
pass "GET /v3/api-docs -> 200"

summary
echo "Result: OK"
