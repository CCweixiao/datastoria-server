import { copyFile, mkdir } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(here, "../../..");
const source = path.join(repoRoot, "docs/api/openapi-baseline.yaml");
const outputDir = path.join(repoRoot, "datastoria-web/docs/public/api");

await mkdir(outputDir, { recursive: true });
await copyFile(source, path.join(outputDir, "openapi.yaml"));
