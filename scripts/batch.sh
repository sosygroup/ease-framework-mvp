#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$PROJECT_ROOT/scripts/compile.sh"

INPUT_FILE="${1:-$PROJECT_ROOT/src/main/resources/config/example-batch.json}"
OUTPUT_DIRECTORY="${2:-$PROJECT_ROOT/output/batch}"

exec java --add-modules jdk.httpserver \
  -cp "$PROJECT_ROOT/build/classes" \
  org.ease.mvp.app.BatchCli \
  "$INPUT_FILE" \
  "$OUTPUT_DIRECTORY"
