#!/usr/bin/env bash
set -euo pipefail

if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle 9.6.0 is required. Install Gradle first, then rerun this script." >&2
  exit 1
fi

gradle wrapper --gradle-version 9.6.0 --distribution-type bin
