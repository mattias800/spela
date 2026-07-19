#!/usr/bin/env bash
# Regenerate the Kotlin OpenAPI client in player/shared-api/ from the
# huma-emitted OpenAPI spec (web/src/generated/openapi.json).
#
# Run after any server API change that should flow through to the player
# app. The output is committed to the repo — CI does not regenerate.
#
# Prereqs:
#   - npx (for openapi-generator-cli)
#   - Go toolchain (only if openapi.json is stale — see below)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
SPEC="$REPO_ROOT/web/src/generated/openapi.json"
OUT_DIR="$REPO_ROOT/player/shared-api"
TMP_DIR="$(mktemp -d -t spela-kotlin-gen.XXXXXX)"
trap 'rm -rf "$TMP_DIR"' EXIT

if [[ ! -f "$SPEC" ]]; then
  echo "OpenAPI spec not found at $SPEC — run npm run openapi:dump in web/ first" >&2
  exit 1
fi

echo "Generating into $TMP_DIR..."
npx -y @openapitools/openapi-generator-cli@2.22.0 generate \
  -i "$SPEC" \
  -g kotlin \
  -o "$TMP_DIR" \
  -p library=multiplatform \
  -p serializationLibrary=kotlinx_serialization \
  -p dateLibrary=kotlinx-datetime \
  -p packageName=com.spela.client \
  -p apiPackage=com.spela.client.apis \
  -p modelPackage=com.spela.client.models \
  --skip-validate-spec

echo "Copying generated commonMain sources to $OUT_DIR/src/commonMain/..."
rm -rf "$OUT_DIR/src/commonMain/kotlin/com/spela/client"
mkdir -p "$OUT_DIR/src/commonMain/kotlin/com/spela/client"
cp -R "$TMP_DIR/src/commonMain/kotlin/com/spela/client/." \
  "$OUT_DIR/src/commonMain/kotlin/com/spela/client/"

echo "Post-processing: strip duplicate @Serializable annotations..."
# openapi-generator 7.21 emits '@Serializable@Serializable' on every data
# class under -g kotlin library=multiplatform. Kotlin compiler rejects it
# as non-repeatable. Strip the second one.
find "$OUT_DIR/src/commonMain/kotlin/com/spela/client/models" -name "*.kt" \
  -exec perl -i -pe 's/\@Serializable\@Serializable/\@Serializable/g' {} +

echo "Post-processing: replace kotlin.Any with JsonElement/JsonObject..."
# 'any' schemas (free-form filters, discriminated metadata unions) render
# as kotlin.Any — which kotlinx.serialization can't serialize. Swap to
# JsonElement/JsonObject so the caller can parse manually.
find "$OUT_DIR/src/commonMain/kotlin/com/spela/client/models" -name "*.kt" \
  -exec perl -i -pe '
    s/kotlin\.collections\.Map<kotlin\.String, kotlin\.Any>/kotlinx.serialization.json.JsonObject/g;
    s/\bkotlin\.Any\b/kotlinx.serialization.json.JsonElement/g;
  ' {} +

echo "Post-processing: free-form object properties -> JsonObject?..."
# The kotlin generator emits `kotlin.String` for free-form object properties
# (type: object, empty additionalProperties) — the server sends a JSON object
# there, so decoding fails at runtime (#720, #1675 regression). This step is
# spec-driven and fails the regen if any free-form property is left as String.
node "$SCRIPT_DIR/fix-freeform-objects.mjs" "$SPEC" \
  "$OUT_DIR/src/commonMain/kotlin/com/spela/client/models"

echo "Post-processing: decode HTML-entity backticks in apis/*.kt..."
# openapi-generator 7.21 HTML-encodes Kotlin-reserved-word backticks
# (&#x60;) in default argument values when a parameter's default matches
# a Kotlin keyword (e.g. `true`/`false` for boolean enums). Decode back
# to literal backticks so the generated code compiles.
find "$OUT_DIR/src/commonMain/kotlin/com/spela/client/apis" -name "*.kt" \
  -exec perl -i -pe 's/&#x60;/`/g' {} +

echo "Post-processing: fix snake_case FormFile references in apis/*.kt..."
# openapi-generator 7.21 emits broken snake_case identifier references
# inside the formData{} block when a FormFile field name contains
# underscores: the parameter is camelCased to 'patchFile' but the body
# reads `append(patch_file)` — a non-existent identifier. This breaks
# compilation. The spec treats all current form fields as camelCase, but
# we guard against regressions by rewriting any `append(snake_case_name)`
# inside an `?.apply { ... }` block to the matching camelCase identifier.
find "$OUT_DIR/src/commonMain/kotlin/com/spela/client/apis" -name "*.kt" \
  -exec perl -i -pe '
    # Match `<name>?.apply { append(<snake>) }` where <snake> contains underscores.
    # Convert <snake> to camelCase (strip underscores, upper the next char).
    s{(\w+)\?\.apply \{ append\(([a-z][a-z0-9]*(?:_[a-z0-9]+)+)\) \}}{
      my ($param, $snake) = ($1, $2);
      my $camel = $snake;
      $camel =~ s/_([a-z0-9])/uc($1)/ge;
      "$param?.apply { append($camel) }";
    }ge;
  ' {} +

echo "Recording source spec hash..."
# CI's openapi-drift job compares this stamp against the committed spec, so
# a server API change can't land without regenerating this client (#1675).
# \r is stripped so the hash is stable across autocrlf settings.
tr -d '\r' < "$SPEC" | sha256sum | awk '{print $1}' > "$OUT_DIR/openapi-spec.sha256"

echo "Staging new + modified files in git..."
# Stage everything under the generated tree, including newly-created
# files. Manual `git add` after a regen is easy to forget — and CI
# only catches it when a missing model is referenced (which it always
# is, since the apis/*.kt always imports the new models).
if command -v git >/dev/null 2>&1 && git -C "$REPO_ROOT" rev-parse --git-dir >/dev/null 2>&1; then
  git -C "$REPO_ROOT" add "$OUT_DIR/src/commonMain/" "$OUT_DIR/openapi-spec.sha256" >/dev/null 2>&1 || true
fi

echo ""
echo "Done. Review with 'git diff --cached -- $OUT_DIR/src/commonMain/'."
echo "Verify with: ./gradlew :shared-api:compileKotlinDesktop :shared-api:compileDebugKotlinAndroid"
