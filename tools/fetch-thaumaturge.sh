#!/usr/bin/env sh
# Install an existing release jar; no upstream source checkout or patching.
set -eu
if [ "$#" -ne 1 ]; then
    echo "Usage: $0 /path/to/thaumaturge-1.21.1-NeoForge-1.0.2.jar" >&2
    exit 2
fi
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
source_jar=$1
if [ ! -f "$source_jar" ]; then echo "Jar not found: $source_jar" >&2; exit 1; fi
metadata=$(unzip -p "$source_jar" META-INF/neoforge.mods.toml)
printf '%s' "$metadata" | grep -Eq 'modId[[:space:]]*=[[:space:]]*"thaumaturge"'
printf '%s' "$metadata" | grep -Eq 'version[[:space:]]*=[[:space:]]*"1\.0\.2"'
target="$root/libs/thaumaturge-1.21.1-NeoForge-1.0.2.jar"
if [ "$source_jar" != "$target" ]; then cp -- "$source_jar" "$target"; fi
printf 'Installed %s\n' "$target"
