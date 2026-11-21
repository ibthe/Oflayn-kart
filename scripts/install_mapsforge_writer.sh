#!/usr/bin/env bash
set -euo pipefail

# Build a small, isolated Osmosis plugin package from the published Mapsforge
# Map Writer Maven artifact. Do not clone/build the full Mapsforge multi-project:
# that root build configures Android sample projects and pulls unrelated AGP
# dependencies which are not needed for map generation.
MAPSFORGE_VERSION="${MAPSFORGE_VERSION:-0.25.0}"
WORK_DIR="${RUNNER_TEMP:-/tmp}/mapsforge-writer-plugin-${MAPSFORGE_VERSION}"
PLUGIN_DIR="${HOME}/.openstreetmap/osmosis/plugins"
EXPECTED_JAR="${PLUGIN_DIR}/mapsforge-map-writer-${MAPSFORGE_VERSION}-jar-with-dependencies.jar"
EXPECTED_PLUGIN_LOADER="org.mapsforge.map.writer.osmosis.MapFileWriterPluginLoader"

if ! command -v osmosis >/dev/null 2>&1; then
  echo "ERROR: Osmosis is not installed. Install the Ubuntu package 'osmosis' first." >&2
  exit 2
fi

if [[ ! -s "$EXPECTED_JAR" ]]; then
  rm -rf "$WORK_DIR"
  mkdir -p "$WORK_DIR" "$PLUGIN_DIR"

  cat > "${WORK_DIR}/settings.gradle" <<'GRADLE_SETTINGS'
rootProject.name = 'mapsforge-writer-plugin-packaging'
GRADLE_SETTINGS

  cat > "${WORK_DIR}/build.gradle" <<'GRADLE_BUILD'
plugins {
    id 'java'
}

def writerVersion = providers.gradleProperty('mapsforgeVersion').getOrElse('0.25.0')
def pluginDescriptorFile = layout.buildDirectory.file('generated-plugin-resources/osmosis-plugins.conf')

repositories {
    mavenCentral()
}

dependencies {
    // Resolve only the published Writer module and its runtime dependency graph.
    // The complete Mapsforge repository is not configured or built.
    implementation("org.mapsforge:mapsforge-map-writer:${writerVersion}")
}

tasks.register('generateOsmosisPluginDescriptor') {
    outputs.file(pluginDescriptorFile)
    doLast {
        def descriptor = pluginDescriptorFile.get().asFile
        descriptor.parentFile.mkdirs()
        descriptor.text = 'org.mapsforge.map.writer.osmosis.MapFileWriterPluginLoader\n'
    }
}

tasks.register('fatJar', Jar) {
    group = 'build'
    description = 'Packages Mapsforge Map Writer and runtime libraries for Osmosis.'
    archiveFileName.set("mapsforge-map-writer-${writerVersion}-jar-with-dependencies.jar")
    destinationDirectory.set(layout.buildDirectory.dir('libs'))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    dependsOn('generateOsmosisPluginDescriptor')
    from(pluginDescriptorFile.map { it.asFile.parentFile })
    from(sourceSets.main.output)
    from {
        def jars = configurations.runtimeClasspath.files
            .findAll { it.isFile() && it.name.endsWith('.jar') }
            .sort { left, right ->
                int leftRank = left.name.startsWith('mapsforge-map-writer-') ? 0 : 1
                int rightRank = right.name.startsWith('mapsforge-map-writer-') ? 0 : 1
                leftRank != rightRank ? leftRank <=> rightRank : left.name <=> right.name
            }
        if (jars.isEmpty()) {
            throw new GradleException('Mapsforge Map Writer runtime artifacts were not resolved.')
        }
        jars.collect { zipTree(it) }
    }

    // Strip signatures from source jars: they cannot remain valid after merging.
    exclude('META-INF/*.SF', 'META-INF/*.DSA', 'META-INF/*.RSA', 'META-INF/INDEX.LIST')
    preserveFileTimestamps = false
    reproducibleFileOrder = true
}
GRADLE_BUILD

  echo "Resolving the published Mapsforge Map Writer ${MAPSFORGE_VERSION} from Maven Central."
  gradle --no-daemon --stacktrace --console=plain \
    --max-workers=2 \
    -p "$WORK_DIR" \
    "-PmapsforgeVersion=${MAPSFORGE_VERSION}" \
    fatJar

  BUILT_JAR="${WORK_DIR}/build/libs/mapsforge-map-writer-${MAPSFORGE_VERSION}-jar-with-dependencies.jar"
  if [[ ! -s "$BUILT_JAR" ]]; then
    echo "ERROR: Mapsforge writer plugin JAR was not produced." >&2
    find "$WORK_DIR/build/libs" -maxdepth 1 -type f -print >&2 || true
    exit 3
  fi

  cp "$BUILT_JAR" "$EXPECTED_JAR"
fi

if ! command -v unzip >/dev/null 2>&1; then
  echo "ERROR: unzip is required to verify the Osmosis plugin descriptor." >&2
  exit 4
fi
PLUGIN_DESCRIPTOR="$(unzip -p "$EXPECTED_JAR" osmosis-plugins.conf 2>/dev/null | tr -d '\r\n' || true)"
if [[ "$PLUGIN_DESCRIPTOR" != "$EXPECTED_PLUGIN_LOADER" ]]; then
  echo "ERROR: Mapsforge plugin JAR is missing the expected osmosis-plugins.conf descriptor." >&2
  echo "Expected: ${EXPECTED_PLUGIN_LOADER}" >&2
  echo "Found: ${PLUGIN_DESCRIPTOR:-<empty>}" >&2
  exit 5
fi

HELP_OUTPUT="$(osmosis --help 2>&1 || true)"
if ! grep -q -- '--mapfile-writer' <<<"$HELP_OUTPUT"; then
  echo "ERROR: Osmosis did not register the Mapsforge --mapfile-writer plugin." >&2
  printf '%s\n' "$HELP_OUTPUT" >&2
  exit 6
fi

echo "Mapsforge Map Writer ${MAPSFORGE_VERSION} is registered with Osmosis."
