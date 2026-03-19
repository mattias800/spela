#!/usr/bin/env bash
# Downloads all non-disc-based No-Intro DAT files from the libretro-database GitHub repo.
# Usage: ./download-dats.sh [output-dir]
#   output-dir defaults to ./dats (relative to this script's directory)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUT_DIR="${1:-$SCRIPT_DIR/dats}"
BASE_URL="https://raw.githubusercontent.com/libretro/libretro-database/master/metadat/no-intro"
MAME_BASE_URL="https://raw.githubusercontent.com/libretro/libretro-database/master/metadat/mame"

# All non-disc-based systems from AbbreviationToLibRetro in scraper.go
# (excludes PSX, SAT, DC, SCD, PS2)
SYSTEMS=(
  "Nintendo - Nintendo Entertainment System"
  "Nintendo - Super Nintendo Entertainment System"
  "Nintendo - Game Boy"
  "Nintendo - Game Boy Color"
  "Nintendo - Game Boy Advance"
  "Nintendo - Nintendo 64"
  "Nintendo - Nintendo DS"
  "Sega - Master System - Mark III"
  "Sega - Mega Drive - Genesis"
  "Sony - PlayStation Portable"
  "SNK - Neo Geo"
  "FBNeo - Arcade Games"
  "NEC - PC Engine - TurboGrafx 16"
  "Atari - 2600"
  "Sega - Game Gear"
  "Sega - 32X"
  "Nintendo - Virtual Boy"
  "Nintendo - Nintendo 3DS"
  "Atari - 5200"
  "Atari - 7800"
  "Atari - Lynx"
  "Atari - Jaguar"
  "SNK - Neo Geo Pocket"
  "Bandai - WonderSwan"
  "NEC - PC-FX"
  "Coleco - ColecoVision"
  "Nintendo - Pokemon Mini"
  "Commodore - 64"
  "DOS"
  "Commodore - Amiga"
)

mkdir -p "$OUT_DIR"

ok=0
fail=0

for system in "${SYSTEMS[@]}"; do
  encoded=$(python3 -c "import urllib.parse; print(urllib.parse.quote('$system'))")
  # MAME/FBNeo DATs live in different directories than No-Intro DATs
  if [ "$system" = "MAME" ]; then
    url="$MAME_BASE_URL/$encoded.dat"
  elif [[ "$system" == FBNeo* ]]; then
    url="https://raw.githubusercontent.com/libretro/libretro-database/master/metadat/fbneo-split/$encoded.dat"
  else
    url="$BASE_URL/$encoded.dat"
  fi
  dest="$OUT_DIR/$system.dat"

  printf "Downloading: %s ... " "$system"
  if curl -fsSL -o "$dest" "$url"; then
    echo "OK"
    ((ok++))
  else
    echo "FAILED"
    ((fail++))
  fi
done

echo ""
echo "Done: $ok downloaded, $fail failed (out of ${#SYSTEMS[@]} systems)"
