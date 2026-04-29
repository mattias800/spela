#!/usr/bin/env node
// One-shot: read player/shared/.../ControllerIcons.kt, find each icon's
// `path { ... }` DSL block, convert the DSL calls back to an SVG path
// `d` string, print one line per icon. Output goes into the new
// ControllerIcons.kt as a Map<String, String>.
//
// Mapping:
//   moveTo(x, y)                                 -> "M x y"
//   moveToRelative(dx, dy)                       -> "m dx dy"
//   lineTo(x, y)                                 -> "L x y"
//   lineToRelative(dx, dy)                       -> "l dx dy"
//   horizontalLineTo(x)                          -> "H x"
//   horizontalLineToRelative(dx)                 -> "h dx"
//   verticalLineTo(y)                            -> "V y"
//   verticalLineToRelative(dy)                   -> "v dy"
//   curveTo(x1, y1, x2, y2, x, y)                -> "C x1 y1 x2 y2 x y"
//   curveToRelative(...)                         -> "c ..."
//   arcTo(rx, ry, theta, lf, sf, x, y)           -> "A rx ry theta {lf:1/0} {sf:1/0} x y"
//   arcToRelative(...)                           -> "a ..."
//   quadToRelative(dx1, dy1, dx, dy)             -> "q dx1 dy1 dx dy"
//   reflectiveCurveToRelative(dx1, dy1, dx, dy)  -> "s dx1 dy1 dx dy"
//   close()                                      -> "Z"

import { readFileSync } from "node:fs";

const SRC = "player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/components/keymapping/ControllerIcons.kt";
const text = readFileSync(SRC, "utf8");

// Each icon is `val Foo: ImageVector by lazy { ImageVector.Builder(...).apply { path(fill = SolidColor(Color.Black)) { <DSL> } }.build() }`.
// We match: the val name, then any chars (lazy), then the path() opening
// brace, then the DSL body up to a line that's just `}` (closing the
// path block) followed by another `}` (closing apply).
const iconRegex = /^\s+val\s+(\w+):\s+ImageVector\s+by\s+lazy\s+\{[\s\S]*?path\(fill\s*=\s*SolidColor\(Color\.Black\)\)\s*\{([\s\S]*?)^\s+\}\s*$\s*\}\s*\.build\(\)/gm;

const results = [];
for (const match of text.matchAll(iconRegex)) {
  const iconName = match[1];
  const dslBody = match[2];
  results.push({ iconName, svg: convert(dslBody) });
}

if (results.length === 0) {
  console.error("ERROR: regex matched 0 icons. Inspect ControllerIcons.kt.");
  process.exit(1);
}

for (const { iconName, svg } of results) {
  console.log(`${iconName}:`);
  console.log(svg);
  console.log("---");
}

function convert(dslBody) {
  const out = [];
  for (const rawLine of dslBody.split("\n")) {
    const line = rawLine.trim();
    if (line === "" || line.startsWith("//")) continue;

    if (/^close\(\s*\)\s*$/.test(line)) {
      out.push("Z");
      continue;
    }

    const m = line.match(/^(\w+)\(([^)]*)\)\s*$/);
    if (!m) {
      throw new Error(`Unrecognised DSL line: ${rawLine}`);
    }
    const cmd = m[1];
    const args = m[2]
      .split(",")
      .map((a) => a.trim())
      .filter(Boolean)
      .map(parseArg);

    out.push(emit(cmd, args));
  }
  return out.join(" ");
}

function parseArg(a) {
  if (a === "true") return 1;
  if (a === "false") return 0;
  const stripped = a.endsWith("f") ? a.slice(0, -1) : a;
  return stripped;
}

function emit(cmd, args) {
  const A = args.join(" ");
  switch (cmd) {
    case "moveTo": return `M${args[0]} ${args[1]}`;
    case "moveToRelative": return `m${args[0]} ${args[1]}`;
    case "lineTo": return `L${args[0]} ${args[1]}`;
    case "lineToRelative": return `l${args[0]} ${args[1]}`;
    case "horizontalLineTo": return `H${args[0]}`;
    case "horizontalLineToRelative": return `h${args[0]}`;
    case "verticalLineTo": return `V${args[0]}`;
    case "verticalLineToRelative": return `v${args[0]}`;
    case "curveTo": return `C${A}`;
    case "curveToRelative": return `c${A}`;
    case "arcTo": return `A${A}`;
    case "arcToRelative": return `a${A}`;
    case "quadToRelative": return `q${A}`;
    case "reflectiveCurveToRelative": return `s${A}`;
    default: throw new Error(`Unmapped command: ${cmd}`);
  }
}
