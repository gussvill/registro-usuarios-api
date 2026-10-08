#!/bin/sh
# Regenerates the interactive HTML and the PNG of every diagram from its JSON source.
#
#   ARCHIFY_HOME=/path/to/archify docs/diagrams/build.sh [name ...]
#
# Requires Node.js 18+, Python 3, Chrome or Chromium, and a checkout of Archify 2.17
# (https://github.com/tt-a1i/archify). ARCHIFY_HOME is the folder that contains bin/archify.mjs;
# CHROME overrides the browser binary. Without arguments every diagram is rebuilt.
set -eu

: "${ARCHIFY_HOME:?set ARCHIFY_HOME to the folder that contains bin/archify.mjs}"
export ARCHIFY_UPDATE_CHECK_DISABLED=1
cd "$(dirname "$0")"

find_chrome() {
  if [ -n "${CHROME:-}" ]; then
    echo "$CHROME"
    return
  fi
  for candidate in google-chrome chromium chromium-browser chrome; do
    if command -v "$candidate" >/dev/null 2>&1; then
      command -v "$candidate"
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
  echo "Chrome or Chromium was not found; set CHROME to its binary" >&2
  exit 1
}

type_of() {
  python3 -c 'import json, sys; print(json.load(open(sys.argv[1], encoding="utf-8"))["diagram_type"])' "$1.json"
}

# The viewer only ships English and Chinese chrome. These three strings are the ones a reader of
# the Spanish diagrams sees first, so they are rewritten after rendering.
localise() {
  python3 - "$1.html" <<'PY'
import re
import sys

path = sys.argv[1]
html = open(path, encoding="utf-8").read()
html = html.replace(">Legend</text>", ">Leyenda</text>")
html = re.sub(r'<html lang="en"', '<html lang="es"', html, count=1)
html = re.sub(r"(<title>[^<]*?) Diagram</title>", r"\1</title>", html, count=1)
open(path, "w", encoding="utf-8").write(html)
PY
}

# The PNG is the inline SVG alone, at twice its size, in the light theme, on a white background
# and with the optional motion switched off.
capture() {
  size=$(python3 -c 'import json, sys; print(*json.load(open(sys.argv[1], encoding="utf-8"))["meta"]["viewBox"], sep=",")' "$1.json")
  python3 - "$1.html" ".$1.capture.html" <<'PY'
import sys

html = open(sys.argv[1], encoding="utf-8").read()
css = """<style>
html,body{background:#ffffff!important;margin:0!important;padding:0!important;overflow:hidden!important}
body>*{visibility:hidden!important}
.diagram-container,.diagram-container *{visibility:visible}
.diagram-container .diagram-nav,.diagram-container>:not(svg){display:none!important}
.diagram-container svg{position:fixed!important;left:0;top:0;width:100vw!important;height:100vh!important;z-index:2147483647;background:#ffffff;transform:none!important}
.diagram-container svg [data-animate]{animation:none!important}
</style></head>"""
open(sys.argv[2], "w", encoding="utf-8").write(html.replace("</head>", css, 1))
PY
  "$(find_chrome)" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=2 \
    --window-size="$size" --screenshot="$PWD/$1.png" "file://$PWD/.$1.capture.html?theme=light" >/dev/null 2>&1
  rm -f ".$1.capture.html"
}

if [ "$#" -eq 0 ]; then
  set -- components registration-sequence registration-sequence-errors
fi

for name in "$@"; do
  node "$ARCHIFY_HOME/bin/archify.mjs" deliver "$(type_of "$name")" "$name.json" "$name.html" --quality showcase
  localise "$name"
  capture "$name"
  echo "$name: $name.html and $name.png rebuilt"
done
