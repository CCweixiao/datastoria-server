// Validates every .jsonl fixture under docs/fixtures/stream against the
// ui-message-chunk JSON Schema (docs/fixtures/stream/schema.json).
//
// Run: npm run validate-fixtures

import { readFileSync, readdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, resolve } from "node:path";
import Ajv2020 from "ajv/dist/2020.js";
import { exit } from "node:process";

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, "..", "..", "..");
const fixturesDir = join(repoRoot, "docs", "fixtures", "stream");
const schemaPath = join(fixturesDir, "schema.json");

const schema = JSON.parse(readFileSync(schemaPath, "utf8"));
const ajv = new Ajv2020({ allErrors: true, strict: false });
const validate = ajv.compile(schema);

const jsonlFiles = readdirSync(fixturesDir).filter((f) => f.endsWith(".jsonl"));

let totalLines = 0;
let totalErrors = 0;

for (const file of jsonlFiles) {
  const fullPath = join(fixturesDir, file);
  const raw = readFileSync(fullPath, "utf8");
  const lines = raw.split("\n").filter((l) => l.trim().length > 0);
  for (let i = 0; i < lines.length; i++) {
    totalLines++;
    let obj;
    try {
      obj = JSON.parse(lines[i]);
    } catch (e) {
      console.error(`✗ ${file}:${i + 1} invalid JSON: ${e.message}`);
      totalErrors++;
      continue;
    }
    if (!validate(obj)) {
      totalErrors++;
      const messages = (validate.errors || [])
        .map((e) => `${e.instancePath || "/"} ${e.message}`)
        .join("; ");
      console.error(`✗ ${file}:${i + 1} schema violation: ${messages}`);
    }
  }
  console.log(`✓ ${file} (${lines.length} chunks)`);
}

console.log("");
console.log(`Validated ${jsonlFiles.length} fixtures, ${totalLines} chunks, ${totalErrors} errors.`);
exit(totalErrors === 0 ? 0 : 1);
