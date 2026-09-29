#!/usr/bin/env bash
set -euo pipefail

APP_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
APP_JAR="$APP_DIR/target/noteworthy-1.0.0.jar"

if ! command -v java >/dev/null 2>&1; then
    printf 'Java 21 or newer is required and must be available on PATH.\n' >&2
    exit 1
fi

if [[ ! -f "$APP_JAR" ]]; then
    printf 'Application JAR not found: %s\nRun "mvn package" from the project directory first.\n' "$APP_JAR" >&2
    exit 1
fi

exec java -jar "$APP_JAR" "$@"