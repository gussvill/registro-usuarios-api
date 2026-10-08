#!/bin/sh
# Regenera las imágenes PNG que incrusta el README a partir de architecture.html.
#
#   docs/diagrams/build.sh
#
# architecture.html es la fuente: los datos de los tres diagramas están en su objeto DIAGRAM y la
# página no necesita ningún paso de compilación. Este script solo toma una captura estática de cada
# vista (la vista general, con todos los elementos visibles) para que el README las pueda mostrar.
#
# Único requisito: Chrome o Chromium. La variable CHROME indica el binario si no está en el PATH.
# Las imágenes solo se reemplazan si Chrome termina bien y la captura no está vacía.
set -eu

cd "$(dirname "$0")"

fail() {
  echo "build.sh: $1" >&2
  exit 1
}

find_chrome() {
  if [ -n "${CHROME:-}" ]; then
    if [ -f "$CHROME" ] && [ -x "$CHROME" ]; then
      echo "$CHROME"
      return
    fi
    if found=$(command -v "$CHROME" 2>/dev/null) && [ -f "$found" ] && [ -x "$found" ]; then
      echo "$found"
      return
    fi
    fail "CHROME=$CHROME no es un ejecutable; indique la ruta de Chrome o de Chromium"
  fi
  for candidate in google-chrome google-chrome-stable chromium chromium-browser chrome; do
    if found=$(command -v "$candidate" 2>/dev/null); then
      echo "$found"
      return
    fi
  done
  for candidate in \
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
    "/Applications/Chromium.app/Contents/MacOS/Chromium"; do
    if [ -x "$candidate" ]; then
      echo "$candidate"
      return
    fi
  done
  fail "no se encontró Chrome ni Chromium; indique el binario en la variable CHROME"
}

# URL file:// de la página. Se escapan el porcentaje (primero, para no escapar dos veces), el
# espacio, la almohadilla y la interrogación, que de otro modo Chrome leería como principio del
# fragmento o de la consulta. Una ruta con un salto de línea no está soportada.
file_url() {
  printf 'file://%s/architecture.html' "$(printf '%s' "$PWD" |
    sed -e 's/%/%25/g' -e 's/ /%20/g' -e 's/#/%23/g' -e 's/?/%3F/g')"
}

[ -f architecture.html ] || fail "no existe architecture.html junto al script"

chrome=$(find_chrome)
url=$(file_url)
work=$(mktemp -d "${TMPDIR:-/tmp}/architecture-build.XXXXXX")
trap 'rm -rf "$work"' EXIT HUP INT TERM

# Una captura por vista: el ancla abre la vista y el nombre del PNG la repite.
# 1440x900 al doble de densidad; a ese tamaño la página entera cabe sin desplazamiento.
for view in componentes registro errores; do
  shot="$work/architecture-$view.png"
  if ! "$chrome" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=2 \
    --window-size=1440,900 --virtual-time-budget=2000 \
    --screenshot="$shot" "$url#$view" >"$work/chrome.log" 2>&1; then
    cat "$work/chrome.log" >&2
    fail "Chrome falló al capturar la vista $view (binario: $chrome)"
  fi
  if [ ! -s "$shot" ]; then
    cat "$work/chrome.log" >&2
    fail "Chrome terminó sin error pero no escribió una captura para la vista $view"
  fi
  mv "$shot" "architecture-$view.png"
  echo "architecture-$view.png regenerada"
done
