import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const sqliteDir = path.join(root, "src/main/resources/db/migration/sqlite");
const postgresDir = path.join(root, "src/main/resources/db/migration/postgresql");
const booleanColumns = new Set([
  "builtin",
  "enabled",
  "is_free",
  "recovery_action_taken",
  "solved",
]);

function convertBooleanColumn(line) {
  const match = line.match(/^(\s*(?:ADD COLUMN\s+)?)([a-z_]+)(\s+)BIGINT\b(.*)$/i);
  if (!match || !booleanColumns.has(match[2].toLowerCase())) {
    return line;
  }
  let suffix = match[4]
    .replace(/\s+CHECK\s*\(\s*[a-z_]+\s+IN\s*\(\s*0\s*,\s*1\s*\)\s*\)/gi, "")
    .replace(/\bDEFAULT\s+0\b/gi, "DEFAULT FALSE")
    .replace(/\bDEFAULT\s+1\b/gi, "DEFAULT TRUE");
  return `${match[1]}${match[2]}${match[3]}BOOLEAN${suffix}`;
}

function convert(source, fileName) {
  let sql = source
    .replaceAll("SQLite dialect", "PostgreSQL dialect")
    .replaceAll("SQLite's", "PostgreSQL's")
    .replaceAll("SQLite", "PostgreSQL")
    .replace(/\bINTEGER NOT NULL PRIMARY KEY AUTOINCREMENT\b/g, "BIGSERIAL PRIMARY KEY")
    .replace(/\bINTEGER\b/g, "BIGINT")
    .replace(/\bBLOB\b/g, "BYTEA")
    .split("\n")
    .map(convertBooleanColumn)
    .join("\n");

  sql = sql
    .replace(/\bjson_valid\(([a-z_]+)\)/gi, "($1::jsonb IS NOT NULL)")
    .replaceAll("json_valid() CHECK", "JSON validity CHECK")
    .replace(
      /length\(checksum\) = 64 AND checksum NOT GLOB '\*\[\^0-9a-f\]\*'/g,
      () => "checksum ~ '^[0-9a-f]{64}$'"
    )
    .replace(
      /\(length\(resolution_digest\) = 64\s+AND resolution_digest NOT GLOB '\*\[\^0-9a-f\]\*'\)/g,
      () => "resolution_digest ~ '^[0-9a-f]{64}$'"
    )
    .replace(
      /(\s+content\s+)TEXT NOT NULL,(?=\n\s+size_bytes)/,
      "$1BYTEA NOT NULL,"
    )
    .replace(
      /SELECT r\.tenant_id, r\.skill_id, s\.revision, r\.resource_path, 'text\/plain', r\.content,/,
      "SELECT r.tenant_id, r.skill_id, s.revision, r.resource_path, 'text/plain',\n       convert_to(r.content, 'UTF8'),"
    )
    .replace(
      /length\(CAST\(r\.content AS BYTEA\)\)/g,
      "octet_length(convert_to(r.content, 'UTF8'))"
    );

  const forbidden = [
    "AUTOINCREMENT",
    " GLOB ",
    " AS BLOB",
    "TINYINT",
    "LONGBLOB",
  ];
  for (const token of forbidden) {
    if (sql.includes(token)) {
      throw new Error(`${fileName}: unconverted SQLite/MySQL token ${token}`);
    }
  }
  if (/\bjson_valid\([a-z_]/i.test(sql)) {
    throw new Error(`${fileName}: unconverted json_valid() call`);
  }
  return sql;
}

fs.mkdirSync(postgresDir, { recursive: true });
for (const fileName of fs.readdirSync(sqliteDir).filter((name) => name.endsWith(".sql")).sort()) {
  const source = fs.readFileSync(path.join(sqliteDir, fileName), "utf8");
  fs.writeFileSync(path.join(postgresDir, fileName), convert(source, fileName));
}

console.log(`Generated PostgreSQL migrations in ${postgresDir}`);
