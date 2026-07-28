#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CLASSES_DIR="$PROJECT_ROOT/build/classes"
JAVA_SOURCES=()
while IFS= read -r source; do
    JAVA_SOURCES+=("$source")
done < <(find "$PROJECT_ROOT/src/main/java" -name '*.java' -print | sort)

mkdir -p "$CLASSES_DIR"
javac --release 21 --add-modules jdk.httpserver -d "$CLASSES_DIR" "${JAVA_SOURCES[@]}"
cp -R "$PROJECT_ROOT/src/main/resources/." "$CLASSES_DIR/"
