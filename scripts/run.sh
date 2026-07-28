#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$PROJECT_ROOT/scripts/compile.sh"
exec java --add-modules jdk.httpserver -cp "$PROJECT_ROOT/build/classes" org.ease.mvp.app.EaseServer "$@"
