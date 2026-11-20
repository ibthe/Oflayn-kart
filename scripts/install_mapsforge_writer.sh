#!/usr/bin/env bash
set -euo pipefail

MAPSFORGE_VERSION="${MAPSFORGE_VERSION:-0.25.0}"
WORK_DIR="${RUNNER_TEMP:-/tmp}/mapsforge-writer-${MAPSFORGE_VERSION}"
PLUGIN_DIR="${HOME}/.openstreetmap/osmosis/plugins"
SOURCE_URL="https://github.com/mapsforge/mapsforge.git"

if ! command -v osmosis >/dev/null 2>&1; then
  echo "ERROR: Osmosis is not installed. Install the Ubuntu package 'osmosis' first." >&2
  exit 2
fi

if [[ -f "${PLUGIN_DIR}/mapsforge-map-writer-${MAPSFORGE_VERSION}-jar-with-dependencies.jar" ]]; then
  echo "Mapsforge Map Writer ${MAPSFORGE_VERSION} already installed."
else
  rm -rf "$WORK_DIR"
  mkdir -p "$(dirname "$WORK_DIR")" "$PLUGIN_DIR"
  git clone --depth 1 --branch "$MAPSFORGE_VERSION" "$SOURCE_URL" "$WORK_DIR"
  (
    cd "$WORK_DIR"
    if [[ -x ./gradlew ]]; then
      ./gradlew --no-daemon --stacktrace :mapsforge-map-writer:fatJar
    elif command -v gradle >/dev/null 2>&1; then
      gradle --no-daemon --stacktrace :mapsforge-map-writer:fatJar
    else
      echo "ERROR: Mapsforge source has no executable Gradle wrapper and Gradle is not on PATH." >&2
      exit 5
    fi
  )

  WRITER_JAR="$(find "$WORK_DIR/mapsforge-map-writer/build/libs" -maxdepth 1 -type f -name '*jar-with-dependencies.jar' -print -quit)"
  if [[ -z "$WRITER_JAR" || ! -s "$WRITER_JAR" ]]; then
    echo "ERROR: Mapsforge writer fat JAR was not produced." >&2
    find "$WORK_DIR/mapsforge-map-writer/build/libs" -maxdepth 1 -type f -print >&2 || true
    exit 3
  fi

  cp "$WRITER_JAR" "${PLUGIN_DIR}/mapsforge-map-writer-${MAPSFORGE_VERSION}-jar-with-dependencies.jar"
fi

HELP_OUTPUT="$(osmosis --help 2>&1 || true)"
if ! grep -q -- '--mapfile-writer' <<<"$HELP_OUTPUT"; then
  echo "ERROR: Osmosis did not register the Mapsforge --mapfile-writer plugin." >&2
  printf '%s\n' "$HELP_OUTPUT" >&2
  exit 4
fi

echo "Mapsforge Map Writer ${MAPSFORGE_VERSION} is registered with Osmosis."
