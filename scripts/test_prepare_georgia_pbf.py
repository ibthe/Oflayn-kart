#!/usr/bin/env python3
"""Exercise verified PBF cache reuse and reject corrupt cached source files."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
PREPARE = ROOT / "scripts/prepare_georgia_pbf.sh"
MIN_BYTES = 10 * 1024 * 1024


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="georgia-pbf-cache-test-") as temporary:
        temp = Path(temporary)
        pbf = temp / "georgia-latest.osm.pbf"
        meta = temp / "georgia-source.json"
        output = temp / "github-output.txt"

        # A deterministic 10 MiB fixture is enough to exercise the same minimum
        # size and MD5 checks used for real country extracts.
        block = b"GeorgiaOffline-PBF-cache-test\n" * 4096
        remaining = MIN_BYTES
        digest = hashlib.md5()
        with pbf.open("wb") as stream:
            while remaining:
                chunk = block[: min(len(block), remaining)]
                stream.write(chunk)
                digest.update(chunk)
                remaining -= len(chunk)

        md5 = digest.hexdigest()
        metadata = {
            "sourceProvider": "geofabrik",
            "sourceLabel": "Geofabrik OpenStreetMap extract: Georgia (country)",
            "sourceUrl": "https://download.geofabrik.de/europe/georgia-latest.osm.pbf",
            "sourcePbfMd5": md5,
            "pbfFile": "georgia-latest.osm.pbf",
            "pbfFileSizeBytes": MIN_BYTES,
        }
        meta.write_text(json.dumps(metadata), encoding="utf-8")

        env = os.environ.copy()
        env.update({
            "OSM_PBF_FILE": str(pbf),
            "OSM_PBF_META_FILE": str(meta),
            "GITHUB_OUTPUT": str(output),
            "FORCE_OSM_REFRESH": "false",
        })

        # Cache hit must be accepted and must not invoke curl/network.
        cache_hit = subprocess.run(
            ["bash", str(PREPARE)], cwd=ROOT, env=env,
            capture_output=True, text=True, check=False,
        )
        if cache_hit.returncode != 0:
            raise SystemExit(f"Valid PBF cache was rejected:\n{cache_hit.stdout}\n{cache_hit.stderr}")
        outputs = output.read_text(encoding="utf-8")
        if "pbf-downloaded=false" not in outputs or f"source-md5={md5}" not in outputs:
            raise SystemExit("Valid PBF cache did not publish expected GitHub outputs.")

        # A corrupt cache must not be accepted. Use a fake curl that fails
        # immediately, proving the script falls back to a fresh download path.
        pbf.write_bytes(b"corrupt")
        fake_bin = temp / "bin"
        fake_bin.mkdir()
        fake_curl = fake_bin / "curl"
        fake_curl.write_text("#!/usr/bin/env bash\nexit 22\n", encoding="utf-8")
        fake_curl.chmod(0o755)
        env["PATH"] = f"{fake_bin}{os.pathsep}{env['PATH']}"
        env["GITHUB_OUTPUT"] = str(temp / "failed-output.txt")
        invalid_cache = subprocess.run(
            ["bash", str(PREPARE)], cwd=ROOT, env=env,
            capture_output=True, text=True, check=False,
        )
        if invalid_cache.returncode == 0:
            raise SystemExit("Corrupt PBF cache was incorrectly accepted.")
        if "No usable cached extract" not in invalid_cache.stdout:
            raise SystemExit("Corrupt-cache test did not exercise the re-download fallback.")

        print("OK: verified PBF cache is reused without network; a corrupt cache is rejected and triggers fresh-source fallback.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
