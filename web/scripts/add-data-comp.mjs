#!/usr/bin/env node
// Add data-comp="<ComponentName>" to the outermost JSX HTML element
// returned by every top-level exported React component in web/src.
//
// "Component" here = an exported function or const-arrow declaration whose
// name starts with an uppercase letter and whose body returns JSX.
// We only stamp HTML elements (lowercase tag name), not custom components
// (uppercase) — those have their own data-comp on whatever HTML they render.
// JSX fragments (<>...</>) are skipped (can't carry attributes).
// Existing data-comp attributes are left untouched.

import { readFileSync, writeFileSync, readdirSync, statSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import ts from "typescript";

const __dirname = dirname(fileURLToPath(import.meta.url));
const SRC = join(__dirname, "..", "src");

const SKIP_DIR_NAMES = new Set([
  "__tests__",
  "__test__",
  "generated",
  "node_modules",
]);

function walk(dir, out) {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    const st = statSync(full);
    if (st.isDirectory()) {
      if (SKIP_DIR_NAMES.has(entry)) continue;
      walk(full, out);
    } else if (st.isFile() && full.endsWith(".tsx")) {
      out.push(full);
    }
  }
  return out;
}

const files = walk(SRC, []);

let totalEdits = 0;
let touchedFiles = 0;

for (const file of files) {
  const source = readFileSync(file, "utf8");
  const sf = ts.createSourceFile(file, source, ts.ScriptTarget.ESNext, true, ts.ScriptKind.TSX);

  /** @type {Array<{ pos: number; insert: string }>} */
  const edits = [];

  function addAttribute(jsxOpening, name, expr) {
    for (const attr of jsxOpening.attributes.properties) {
      if (ts.isJsxAttribute(attr) && attr.name && attr.name.getText(sf) === name) {
        return false;
      }
    }
    const pos = jsxOpening.tagName.getEnd();
    edits.push({ pos, insert: ` ${name}="${expr}"` });
    return true;
  }

  function isLowercaseTag(tagText) {
    const c = tagText[0];
    return c && c === c.toLowerCase() && c !== c.toUpperCase();
  }

  function findOutermostJsxOpening(returnedExpr) {
    let e = returnedExpr;
    while (e && ts.isParenthesizedExpression(e)) e = e.expression;
    if (!e) return null;
    if (ts.isJsxElement(e)) {
      const tag = e.openingElement.tagName.getText(sf);
      if (isLowercaseTag(tag)) return e.openingElement;
      return null;
    }
    if (ts.isJsxSelfClosingElement(e)) {
      const tag = e.tagName.getText(sf);
      if (isLowercaseTag(tag)) return e;
      return null;
    }
    return null;
  }

  function getReturnedJsx(fn) {
    if (ts.isArrowFunction(fn) && !ts.isBlock(fn.body)) return fn.body;
    if (!fn.body || !ts.isBlock(fn.body)) return null;
    for (const stmt of fn.body.statements) {
      if (ts.isReturnStatement(stmt) && stmt.expression) return stmt.expression;
    }
    return null;
  }

  function processComponent(name, fn) {
    if (!/^[A-Z]/.test(name)) return;
    const expr = getReturnedJsx(fn);
    if (!expr) return;
    const opening = findOutermostJsxOpening(expr);
    if (!opening) return;
    if (addAttribute(opening, "data-comp", name)) totalEdits++;
  }

  for (const stmt of sf.statements) {
    if (ts.isFunctionDeclaration(stmt) && stmt.name) {
      processComponent(stmt.name.text, stmt);
    } else if (ts.isVariableStatement(stmt)) {
      for (const decl of stmt.declarationList.declarations) {
        if (
          decl.name &&
          ts.isIdentifier(decl.name) &&
          decl.initializer &&
          (ts.isArrowFunction(decl.initializer) || ts.isFunctionExpression(decl.initializer))
        ) {
          processComponent(decl.name.text, decl.initializer);
        }
      }
    }
  }

  if (edits.length === 0) continue;

  edits.sort((a, b) => b.pos - a.pos);
  let out = source;
  for (const { pos, insert } of edits) {
    out = out.slice(0, pos) + insert + out.slice(pos);
  }
  writeFileSync(file, out);
  touchedFiles++;
}

console.log(`added data-comp to ${totalEdits} components across ${touchedFiles} files`);
