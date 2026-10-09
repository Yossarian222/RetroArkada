#!/usr/bin/env bash
# Stiahne libretro jadrá z buildbot.libretro.com do app/src/main/jniLibs/arm64-v8a.
# Súbory sa premenujú na lib<jadro>_libretro_android.so, aby ich Android pri inštalácii rozbalil.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/app/src/main/jniLibs/arm64-v8a"
BASE="${CORES_BASE_URL:-https://buildbot.libretro.com/nightly/android/latest/arm64-v8a}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$OUT"

failed=()
while read -r core; do
    [[ -z "$core" || "$core" == \#* ]] && continue
    target="$OUT/lib${core}_libretro_android.so"
    if [[ -s "$target" ]]; then
        echo "✓ $core (z cache)"
        continue
    fi
    echo "↓ $core"
    if curl -fsSL --retry 4 --retry-delay 3 -o "$TMP/$core.zip" "$BASE/${core}_libretro_android.so.zip" \
        && unzip -o -q "$TMP/$core.zip" -d "$TMP/$core"; then
        so="$(find "$TMP/$core" -name '*.so' | head -n1)"
        mv "$so" "$target"
    else
        failed+=("$core")
    fi
done < "$ROOT/scripts/cores.txt"

ls -la "$OUT"
if (( ${#failed[@]} )); then
    echo "Nepodarilo sa stiahnuť: ${failed[*]}" >&2
    exit 1
fi
