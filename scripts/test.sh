#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MAIN_CLASSES="$PROJECT_ROOT/build/classes"
TEST_CLASSES="$PROJECT_ROOT/build/test-classes"
TEST_SOURCES=()
while IFS= read -r source; do
    TEST_SOURCES+=("$source")
done < <(find "$PROJECT_ROOT/src/test/java" -name '*.java' -print | sort)

"$PROJECT_ROOT/scripts/compile.sh"
mkdir -p "$TEST_CLASSES"
javac --release 21 --add-modules jdk.httpserver -cp "$MAIN_CLASSES" -d "$TEST_CLASSES" "${TEST_SOURCES[@]}"
exec java --add-modules jdk.httpserver -cp "$MAIN_CLASSES:$TEST_CLASSES" org.ease.mvp.EaseEngineTest
