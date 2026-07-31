import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import addFormats from "ajv-formats";
import Ajv2020 from "ajv/dist/2020.js";

const here = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(here, "../../..");
const fixturesDir = path.join(repoRoot, "docs/fixtures/stream");
const schema = JSON.parse(await readFile(path.join(fixturesDir, "schema.json"), "utf8"));
const ajv = new Ajv2020({ allErrors: true, strict: false });

addFormats(ajv);
const validate = ajv.compile(schema);
const fixtureFiles = (await readdir(fixturesDir)).filter((file) => file.endsWith(".jsonl"));
let chunkCount = 0;
let errorCount = 0;

for (const file of fixtureFiles) {
  const lines = (await readFile(path.join(fixturesDir, file), "utf8"))
    .split("\n")
    .filter((line) => line.trim());

  for (const [index, line] of lines.entries()) {
    chunkCount++;
    try {
      const chunk = JSON.parse(line);
      if (!validate(chunk)) {
        const errors = (validate.errors || [])
          .map((error) => `${error.instancePath || "/"} ${error.message}`)
          .join("; ");
        console.error(`✗ ${file}:${index + 1} ${errors}`);
        errorCount++;
      }
    } catch (error) {
      console.error(`✗ ${file}:${index + 1} invalid JSON: ${error.message}`);
      errorCount++;
    }
  }

  console.log(`✓ ${file} (${lines.length} chunks)`);
}

console.log(`Validated ${fixtureFiles.length} fixtures and ${chunkCount} chunks.`);
if (errorCount > 0) process.exitCode = 1;
