#!/usr/bin/env node
// Post-processing step for regen-kotlin-api.sh.
//
// openapi-generator's kotlin generator emits `kotlin.String` for free-form
// object properties (`type: object` with empty/true additionalProperties and
// no `properties`). The server sends a JSON object for those fields, so the
// generated model fails to deserialize at runtime (#720, #1675).
//
// This script is spec-driven: it finds every free-form object property in
// the OpenAPI spec, locates the matching generated model file, and rewrites
// the property to `kotlinx.serialization.json.JsonObject? = null`. It exits
// non-zero if a free-form property is left as `kotlin.String`, so a future
// generator or spec change can never silently reintroduce the bug.
//
// Usage: node fix-freeform-objects.mjs <openapi.json> <models-dir>

import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { join } from "node:path";

const [specPath, modelsDir] = process.argv.slice(2);
if (!specPath || !modelsDir) {
  console.error("usage: fix-freeform-objects.mjs <openapi.json> <models-dir>");
  process.exit(2);
}

const spec = JSON.parse(readFileSync(specPath, "utf8"));

const isFreeForm = (s) =>
  s !== null &&
  typeof s === "object" &&
  s.type === "object" &&
  !s.properties &&
  (s.additionalProperties === true ||
    (typeof s.additionalProperties === "object" &&
      s.additionalProperties !== null &&
      Object.keys(s.additionalProperties).length === 0));

const targets = [];
for (const [schemaName, schema] of Object.entries(spec.components?.schemas ?? {})) {
  for (const [propName, propSchema] of Object.entries(schema.properties ?? {})) {
    if (isFreeForm(propSchema)) targets.push({ schemaName, propName });
  }
}

let failed = false;
for (const { schemaName, propName } of targets) {
  const file = join(modelsDir, `${schemaName}.kt`);
  if (!existsSync(file)) {
    console.error(`fix-freeform-objects: model file not found for ${schemaName}.${propName}: ${file}`);
    failed = true;
    continue;
  }
  const src = readFileSync(file, "utf8");
  // Matches e.g.:
  //   @SerialName(value = "metadata") @Required val metadata: kotlin.String,
  //   @SerialName(value = "metadata") val metadata: kotlin.String? = null,
  const re = new RegExp(
    `(@SerialName\\(value = "${propName}"\\)) (?:@Required )?val ${propName}: kotlin\\.String[^,\\n]*`,
    "g",
  );
  const out = src.replace(
    re,
    `$1 val ${propName}: kotlinx.serialization.json.JsonObject? = null`,
  );
  if (out !== src) {
    writeFileSync(file, out);
    console.log(`fix-freeform-objects: rewrote ${schemaName}.${propName} -> JsonObject?`);
  } else if (new RegExp(`val ${propName}: kotlin\\.String`).test(src)) {
    console.error(
      `fix-freeform-objects: ${schemaName}.${propName} is a free-form object but generated as kotlin.String in an unexpected form — update this script's rewrite pattern`,
    );
    failed = true;
  } else {
    console.log(`fix-freeform-objects: ${schemaName}.${propName} already non-String, skipping`);
  }
}

if (failed) process.exit(1);
