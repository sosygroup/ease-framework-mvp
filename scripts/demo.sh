#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$PROJECT_ROOT/scripts/compile.sh"
if [ "$#" -eq 0 ]; then
  set -- demo
fi
exec java -cp "$PROJECT_ROOT/build/classes" org.ease.mvp.app.EaseCli "$@"
