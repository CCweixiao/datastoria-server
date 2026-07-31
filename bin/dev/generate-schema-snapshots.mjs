#!/usr/bin/env node

import { readFile, readdir, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

const root = resolve(import.meta.dirname, "../..");
const checkOnly = process.argv.includes("--check");
const dialects = ["sqlite", "mysql"];

for (const dialect of dialects) {
  const migrationDir = resolve(
    root,
    "datastoria-dao/src/main/resources/db/migration",
    dialect,
  );
  const files = (await readdir(migrationDir))
    .filter((name) => /^V\d+__.+\.sql$/.test(name))
    .sort((left, right) => version(left) - version(right));
  const ddl = [];

  for (const file of files) {
    const sql = await readFile(resolve(migrationDir, file), "utf8");
    const uncommented = sql
      .split("\n")
      .filter((line) => !line.trimStart().startsWith("--"))
      .join("\n");
    for (const statement of statements(uncommented)) {
      const executable = statement.trim();
      if (/^(CREATE|ALTER)\s+/i.test(executable)) {
        ddl.push(`-- Source: ${file}\n${executable};`);
      }
    }
  }

  const output =
    `-- GENERATED FILE. DO NOT EDIT DIRECTLY.\n` +
    `-- Regenerate with: node bin/dev/generate-schema-snapshots.mjs\n` +
    `-- Deployment helper for a NEW ${dialect.toUpperCase()} database at Flyway V${version(files.at(-1))}.\n` +
    `-- Application startup continues to use db/migration/${dialect}; this file is not auto-run.\n\n` +
    ddl.join("\n\n") +
    "\n";
  const outputPath = resolve(
    root,
    "datastoria-dao/src/main/resources/db/schema",
    dialect,
    "schema.sql",
  );

  if (checkOnly) {
    const current = await readFile(outputPath, "utf8");
    if (current !== output) {
      throw new Error(`${outputPath} is stale; regenerate schema snapshots`);
    }
  } else {
    await writeFile(outputPath, output);
  }
}

function version(name) {
  return Number(name.slice(1, name.indexOf("__")));
}

function statements(sql) {
  const result = [];
  let current = "";
  let quote = null;

  for (let index = 0; index < sql.length; index++) {
    const char = sql[index];
    current += char;
    if (quote) {
      if (char === quote && sql[index - 1] !== "\\") {
        if (sql[index + 1] === quote) {
          current += sql[++index];
        } else {
          quote = null;
        }
      }
    } else if (char === "'" || char === '"') {
      quote = char;
    } else if (char === ";") {
      result.push(current.slice(0, -1));
      current = "";
    }
  }
  if (current.trim()) {
    result.push(current);
  }
  return result;
}
