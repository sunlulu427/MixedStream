#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOC_DIR="$ROOT_DIR/docs"
OUT_DIR="$DOC_DIR/generated"

if ! command -v plantuml >/dev/null 2>&1; then
  echo "[render_docs] plantuml command not found" >&2
  exit 1
fi

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

timestamp="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

for puml in "$DOC_DIR"/*.puml; do
  [[ -f "$puml" ]] || continue
  name="$(basename "$puml" .puml)"

  plantuml -tpng -o generated "$puml"

  title="$(python3 - <<'PY'
import sys

def titleize(value: str) -> str:
    words = value.replace('_', ' ').split()
    return ' '.join(w.capitalize() for w in words)

print(titleize(sys.argv[1]))
PY
"$name")"

  cat >"$OUT_DIR/$name.md" <<EOF
# $title

![${title}](./$name.png)

- Source: \\`$name.puml\\`
- Generated: $timestamp UTC
EOF
done

echo "[render_docs] Generated artifacts in $OUT_DIR"
