#!/usr/bin/env bash
#
# Verificaciones de aceptación para una instancia en ejecución de la API de registro de usuarios.
#
#   scripts/acceptance.sh                              # http://localhost:8080
#   BASE_URL=http://localhost:9090 scripts/acceptance.sh
#
# Solo necesita bash y curl. Se puede ejecutar cualquier cantidad de veces contra la misma instancia: las
# verificaciones de registro usan una dirección única en cada ejecución. El script se detiene en la primera
# verificación fallida, imprime un resumen y termina con un estado distinto de cero.

set -u

BASE_URL="${BASE_URL:-http://localhost:8080}"
WAIT_SECONDS="${WAIT_SECONDS:-30}"

# El ejemplo del enunciado con una dirección única, para que una segunda ejecución (o el curl del README) no
# la encuentre ya registrada. El nombre, la contraseña y los teléfonos quedan como en el enunciado.
RUN_EMAIL="acceptance-$(date +%s)-${RANDOM}${RANDOM}@rodriguez.org"
STATEMENT_BODY='{"name":"Juan Rodriguez","email":"'"${RUN_EMAIL}"'","password":"hunter2","phones":[{"number":"1234567","citycode":"1","contrycode":"57"}]}'

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

PASSED=0
STATUS=""
BODY=""
HEADERS=""

# --- utilidades ------------------------------------------------------------------------------

summary() {
  echo
  echo "Resumen: ${PASSED} verificación(es) aprobada(s) contra ${BASE_URL}"
}

fail() {
  echo "  FALLO $1"
  echo "        estado:      ${STATUS}"
  echo "        encabezados: $(printf '%s' "$HEADERS" | tr '\r\n' '  ')"
  echo "        cuerpo:      ${BODY}"
  summary
  echo "Resultado: FALLÓ"
  exit 1
}

pass() {
  PASSED=$((PASSED + 1))
  echo "  ok    $1"
}

# request <argumentos de curl...>: guarda el estado, los encabezados y el cuerpo de una llamada.
request() {
  STATUS="$(curl -s -o "$WORK_DIR/body" -D "$WORK_DIR/headers" -w '%{http_code}' "$@")"
  BODY="$(cat "$WORK_DIR/body")"
  HEADERS="$(cat "$WORK_DIR/headers")"
}

post_json() {
  request -X POST "${BASE_URL}/api/v1/users" -H 'Content-Type: application/json' "$@"
}

expect_status() { # <esperado> <etiqueta>
  [ "$STATUS" = "$1" ] || fail "$2: se esperaba el estado $1"
}

expect_header_starts_with() { # <encabezado> <prefijo> <etiqueta>
  local value
  value="$(printf '%s' "$HEADERS" | tr -d '\r' | grep -i "^$1:" | head -n 1 | cut -d: -f2- | sed 's/^ *//')"
  case "$value" in
    "$2"*) ;;
    *) fail "$3: el encabezado $1 es '${value}', se esperaba que empezara con '$2'" ;;
  esac
}

expect_body() { # <cuerpo exacto> <etiqueta>
  [ "$BODY" = "$1" ] || fail "$2: se esperaba el cuerpo $1"
}

expect_body_contains() { # <fragmento> <etiqueta>
  case "$BODY" in
    *"$1"*) ;;
    *) fail "$2: el cuerpo no contiene $1" ;;
  esac
}

expect_body_lacks() { # <fragmento> <etiqueta>
  case "$BODY" in
    *"$1"*) fail "$2: el cuerpo no debe contener $1" ;;
    *) ;;
  esac
}

# Un cuerpo que es exactamente un objeto {"mensaje": "..."}.
expect_mensaje_only() { # <etiqueta>
  printf '%s' "$BODY" | grep -Eq '^\{"mensaje":"[^"]+"\}$' || fail "$1: el cuerpo no es un único objeto mensaje"
}

# --- esperar a la aplicación -------------------------------------------------------------------

echo "Verificaciones de aceptación contra ${BASE_URL}"
waited=0
until curl -s -o /dev/null "${BASE_URL}/v3/api-docs"; do
  if [ "$waited" -ge "$WAIT_SECONDS" ]; then
    echo "La aplicación no respondió en ${BASE_URL} dentro de ${WAIT_SECONDS} segundos."
    echo "Resultado: FALLÓ"
    exit 1
  fi
  sleep 1
  waited=$((waited + 1))
done

# --- registro ----------------------------------------------------------------------------------

echo
echo "Registro"

post_json -d "$STATEMENT_BODY"
expect_status 201 "cuerpo del enunciado"
expect_header_starts_with content-type application/json "cuerpo del enunciado"
expect_header_starts_with cache-control no-store "cuerpo del enunciado"
for key in id name email phones created modified last_login token isactive; do
  expect_body_contains "\"${key}\":" "cuerpo del enunciado, clave ${key}"
done
expect_body_contains '"isactive":true' "cuerpo del enunciado"
expect_body_contains '"contrycode":"57"' "cuerpo del enunciado"
expect_body_lacks "password" "cuerpo del enunciado"
expect_body_lacks "hunter2" "cuerpo del enunciado"
printf '%s' "$BODY" | grep -Eq '"token":"[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+"' \
  || fail "cuerpo del enunciado: el token no es un JWT de tres partes"
pass "cuerpo del enunciado -> 201 con Cache-Control: no-store, id, created, modified, last_login, token, isactive y sin contraseña"

post_json -d "$STATEMENT_BODY"
expect_status 409 "registro repetido"
expect_body '{"mensaje":"El correo ya registrado"}' "registro repetido"
pass "registro repetido -> 409 {\"mensaje\":\"El correo ya registrado\"}"

# --- entradas hostiles e inválidas ---------------------------------------------------------------

echo
echo "Entradas inválidas"

post_json -d '{"name":"","email":"bad","password":"x"}'
expect_status 400 "campos inválidos"
expect_mensaje_only "campos inválidos"
pass "campos inválidos -> 400 con un único mensaje"

post_json -d '{"name":'
expect_status 400 "JSON mal formado"
expect_body '{"mensaje":"El cuerpo de la solicitud no es válido"}' "JSON mal formado"
pass "JSON mal formado -> 400"

# --- errores que produce el framework ------------------------------------------------------------

echo
echo "Errores del framework"

request -X GET "${BASE_URL}/api/v1/users"
expect_status 405 "GET sobre la ruta de registro"
expect_header_starts_with allow POST "GET sobre la ruta de registro"
expect_mensaje_only "GET sobre la ruta de registro"
pass "GET /api/v1/users -> 405 con Allow: POST"

request -X GET "${BASE_URL}/nope"
expect_status 404 "ruta desconocida"
expect_mensaje_only "ruta desconocida"
pass "GET /nope -> 404"

request -X POST "${BASE_URL}/api/v1/users" -H 'Content-Type: text/plain' -d 'plain text'
expect_status 415 "cuerpo text/plain"
expect_mensaje_only "cuerpo text/plain"
pass "POST text/plain -> 415"

request -X POST "${BASE_URL}/api/v1/users" -H 'Content-Type: application/json' -H 'Accept: application/xml' \
  -d '{"name":"Ana Perez","email":"ana@dominio.cl","password":"hunter2","phones":[]}'
expect_status 406 "Accept application/xml"
expect_header_starts_with content-type application/json "Accept application/xml"
expect_mensaje_only "Accept application/xml"
pass "Accept: application/xml -> 406 con un cuerpo JSON"

# --- endpoints de documentación ------------------------------------------------------------------

echo
echo "Documentación"

request -L -X GET "${BASE_URL}/swagger-ui.html"
expect_status 200 "Swagger UI"
pass "GET /swagger-ui.html -> 200"

request -X GET "${BASE_URL}/v3/api-docs"
expect_status 200 "documento OpenAPI"
expect_body_contains '"openapi"' "documento OpenAPI"
pass "GET /v3/api-docs -> 200"

summary
echo "Resultado: OK"
