#!/usr/bin/env bash
# Verify a packaged Spela.app carries its own native dependencies.
#
# The build machine's Homebrew paths must never end up as load commands in the
# shipped app: MoltenVK (brew molten-vk) is not a macOS system library, so a
# baked-in /opt/homebrew/... reference makes the app die at launch with
# UnsatisfiedLinkError on every Mac that lacks the formula. See #1687.
#
# Usage: verify-macos-bundle.sh <path/to/Spela.app>
set -euo pipefail

APP_DIR="${1:-}"
if [ ! -d "$APP_DIR" ]; then
  echo "usage: $0 <path to Spela.app>" >&2
  exit 2
fi

APP_LIB_DIR="$APP_DIR/Contents/app"
if [ ! -d "$APP_LIB_DIR" ]; then
  echo "no Contents/app in $APP_DIR" >&2
  exit 2
fi

offenders=""
checked=0
for f in "$APP_LIB_DIR"/*; do
  [ -f "$f" ] || continue
  file "$f" | grep -q 'Mach-O' || continue
  checked=$((checked + 1))
  # A dylib's own install name is the first line of otool -L output; exclude it
  # so only real dependencies are inspected.
  own_id=$(otool -D "$f" | tail -n +2 | tr -d '\t ' || true)
  deps=$(otool -L "$f" | tail -n +2 | sed -e 's/ (compatibility.*//' -e 's/^[[:space:]]*//')
  while IFS= read -r dep; do
    [ -n "$dep" ] || continue
    [ "$dep" = "$own_id" ] && continue
    case "$dep" in
      /usr/lib/* | /System/*) ;;                       # system, always present
      @loader_path/* | @rpath/* | @executable_path/*) ;; # bundle-relative
      /*) offenders="$offenders$(basename "$f") -> $dep"$'\n' ;;
    esac
  done <<< "$deps"
done

if [ "$checked" -eq 0 ]; then
  echo "no Mach-O files found in $APP_LIB_DIR — native libs were not bundled" >&2
  exit 1
fi

if [ -n "$offenders" ]; then
  echo "Packaged app depends on dylibs outside the bundle:" >&2
  printf '%s' "$offenders" >&2
  echo "Bundle them next to the library and repoint with install_name_tool." >&2
  exit 1
fi

echo "OK: $checked Mach-O file(s) in Contents/app depend only on system or bundled libraries"
