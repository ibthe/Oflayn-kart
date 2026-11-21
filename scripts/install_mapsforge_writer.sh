#!/usr/bin/env bash
set -euo pipefail

# Install the Mapsforge Map Writer into the classpath used by Ubuntu's
# Debian-packaged Osmosis. The package launcher uses /etc/osmosis/plexus.conf
# and does not automatically add ~/.openstreetmap/osmosis/plugins to the
# classpath for this osmosis-plugins.conf style plugin.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
MAPSFORGE_VERSION="${MAPSFORGE_VERSION:-0.25.0}"
WORK_DIR="${RUNNER_TEMP:-/tmp}/mapsforge-writer-plugin-${MAPSFORGE_VERSION}"
OSMOSIS_SYSTEM_HOME="${OSMOSIS_SYSTEM_HOME:-/usr/share/osmosis}"
OSMOSIS_PLEXUS_CONF="${OSMOSIS_PLEXUS_CONF:-/etc/osmosis/plexus.conf}"
EXPECTED_JAR_NAME="mapsforge-map-writer-${MAPSFORGE_VERSION}-jar-with-dependencies.jar"
BUILT_JAR="${WORK_DIR}/build/libs/${EXPECTED_JAR_NAME}"
SYSTEM_JAR="${OSMOSIS_SYSTEM_HOME}/${EXPECTED_JAR_NAME}"
EXPECTED_PLUGIN_LOADER="org.mapsforge.map.writer.osmosis.MapFileWriterPluginLoader"
SMOKE_OSM="${ROOT_DIR}/scripts/fixtures/mapsforge-writer-smoke.osm"
SMOKE_MAP="${RUNNER_TEMP:-/tmp}/mapsforge-writer-smoke-${MAPSFORGE_VERSION}.map"

if ! command -v osmosis >/dev/null 2>&1; then
  echo "ERROR: Osmosis is not installed. Install the Ubuntu package 'osmosis' first." >&2
  exit 2
fi
if [[ ! -f "$OSMOSIS_PLEXUS_CONF" ]]; then
  echo "ERROR: Debian Osmosis Plexus configuration was not found: ${OSMOSIS_PLEXUS_CONF}" >&2
  echo "The Ubuntu package should provide /etc/osmosis/plexus.conf." >&2
  exit 2
fi
if [[ ! -f "$SMOKE_OSM" ]]; then
  echo "ERROR: Mapsforge smoke-test fixture is missing: ${SMOKE_OSM}" >&2
  exit 2
fi
if ! command -v unzip >/dev/null 2>&1; then
  echo "ERROR: unzip is required to verify the Osmosis plugin JAR." >&2
  exit 2
fi

mkdir -p "$WORK_DIR"

# Build once per job. The resulting jar is a fat JAR from the published
# Mapsforge Maven artifact; we intentionally do not configure the upstream
# multi-project Gradle build.
if [[ ! -s "$BUILT_JAR" ]]; then
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
    description = 'Packages Mapsforge Map Writer and runtime libraries for Debian Osmosis.'
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

    exclude('META-INF/*.SF', 'META-INF/*.DSA', 'META-INF/*.RSA', 'META-INF/INDEX.LIST')
    preserveFileTimestamps = false
    reproducibleFileOrder = true
}
GRADLE_BUILD

  echo "Resolving published Mapsforge Map Writer ${MAPSFORGE_VERSION} from Maven Central."
  gradle --no-daemon --stacktrace --console=plain \
    --max-workers=2 \
    -p "$WORK_DIR" \
    "-PmapsforgeVersion=${MAPSFORGE_VERSION}" \
    fatJar

  if [[ ! -s "$BUILT_JAR" ]]; then
    echo "ERROR: Mapsforge writer plugin JAR was not produced: ${BUILT_JAR}" >&2
    find "$WORK_DIR/build/libs" -maxdepth 1 -type f -print >&2 || true
    exit 3
  fi
fi

PLUGIN_DESCRIPTOR="$(unzip -p "$BUILT_JAR" osmosis-plugins.conf 2>/dev/null | tr -d '\r\n' || true)"
if [[ "$PLUGIN_DESCRIPTOR" != "$EXPECTED_PLUGIN_LOADER" ]]; then
  echo "ERROR: Mapsforge JAR has an invalid osmosis-plugins.conf descriptor." >&2
  echo "Expected: ${EXPECTED_PLUGIN_LOADER}" >&2
  echo "Found: ${PLUGIN_DESCRIPTOR:-<empty>}" >&2
  exit 4
fi
if ! unzip -Z1 "$BUILT_JAR" | grep -qx 'org/mapsforge/map/writer/osmosis/MapFileWriterPluginLoader.class'; then
  echo "ERROR: Mapsforge plugin loader class is missing from the fat JAR." >&2
  exit 4
fi

# Debian/Ubuntu's Osmosis package uses /etc/osmosis/plexus.conf. The classpath
# configuration must explicitly load the writer JAR; placing it only in the
# user JPF plugins folder does not expose the osmosis-plugins.conf resource.
install_system_file() {
  local source_file="$1"
  local target_file="$2"
  local target_dir
  target_dir="$(dirname "$target_file")"
  if [[ -w "$target_dir" ]]; then
    install -D -m 0644 "$source_file" "$target_file"
  elif command -v sudo >/dev/null 2>&1; then
    sudo install -D -m 0644 "$source_file" "$target_file"
  else
    echo "ERROR: Cannot install ${target_file}; root privileges are required." >&2
    return 1
  fi
}

append_plexus_load() {
  local config_file="$1"
  local jar_path="$2"
  local directive="load ${jar_path}"

  if grep -Fqx "$directive" "$config_file"; then
    echo "Plexus already loads ${jar_path}."
    return
  fi

  if [[ -w "$config_file" ]]; then
    printf '\n%s\n' "$directive" >> "$config_file"
  elif command -v sudo >/dev/null 2>&1; then
    printf '\n%s\n' "$directive" | sudo tee -a "$config_file" >/dev/null
  else
    echo "ERROR: Cannot update ${config_file}; root privileges are required." >&2
    return 1
  fi
}

install_system_file "$BUILT_JAR" "$SYSTEM_JAR"
append_plexus_load "$OSMOSIS_PLEXUS_CONF" "$SYSTEM_JAR"

# Remove the ineffective legacy JPF copy from previous runs. Mapsforge uses
# Osmosis' classpath osmosis-plugins.conf discovery, not an arbitrary JPF zip.
LEGACY_PLUGIN="${HOME}/.openstreetmap/osmosis/plugins/${EXPECTED_JAR_NAME}"
if [[ -f "$LEGACY_PLUGIN" ]]; then
  rm -f "$LEGACY_PLUGIN"
fi

# Exercise the actual writer task; generic CLI help output is not a reliable
# plugin-registration check.
rm -f "$SMOKE_MAP"
export JAVACMD_OPTIONS="${JAVACMD_OPTIONS:--Xms256m -Xmx1g}"
set +e
osmosis \
  --read-xml file="$SMOKE_OSM" \
  --mapfile-writer file="$SMOKE_MAP" \
      type=hd \
      bbox=41.70,44.81,41.73,44.84 \
      map-start-position=41.7151,44.8271 \
      map-start-zoom=12
SMOKE_STATUS=$?
set -e

if [[ "$SMOKE_STATUS" -ne 0 || ! -s "$SMOKE_MAP" ]]; then
  echo "ERROR: Osmosis could not execute the Mapsforge writer smoke test." >&2
  echo "Writer JAR: ${SYSTEM_JAR}" >&2
  echo "Plexus config: ${OSMOSIS_PLEXUS_CONF}" >&2
  echo "Expected config directive: load ${SYSTEM_JAR}" >&2
  echo "The full map generation step will not run until this smoke test succeeds." >&2
  exit 5
fi

SMOKE_SIZE="$(stat -c '%s' "$SMOKE_MAP")"
rm -f "$SMOKE_MAP"
echo "Mapsforge Map Writer ${MAPSFORGE_VERSION} is on the Debian Osmosis classpath and passed a real writer smoke test (${SMOKE_SIZE} bytes)."
