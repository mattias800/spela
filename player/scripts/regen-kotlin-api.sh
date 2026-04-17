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

echo "Post-processing: decode HTML-entity backticks in apis/*.kt..."
# openapi-generator 7.21 HTML-encodes Kotlin-reserved-word backticks
# (&#x60;) in default argument values when a parameter's default matches
# a Kotlin keyword (e.g. `true`/`false` for boolean enums). Decode back
# to literal backticks so the generated code compiles.
find "$OUT_DIR/src/commonMain/kotlin/com/spela/client/apis" -name "*.kt" \
  -exec perl -i -pe 's/&#x60;/`/g' {} +

echo ""
echo "Done. Review and commit the changes under $OUT_DIR/src/commonMain/."
echo "Verify with: ./gradlew :shared-api:compileKotlinDesktop :shared-api:compileDebugKotlinAndroid"
