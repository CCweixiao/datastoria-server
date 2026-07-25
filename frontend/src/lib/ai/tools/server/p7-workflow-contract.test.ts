import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { SERVER_TOOL_NAMES } from "./server-tool-names";

describe("P7 Java/frontend workflow tool contract", () => {
  const fixture = JSON.parse(
    readFileSync(resolve(process.cwd(), "../docs/fixtures/tools/p7-workflow-contract.json"), "utf8")
  );

  for (const name of [
    "generate_sql",
    "optimize_sql",
    "generate_visualization",
    "search_file",
    "read_file",
  ] as const) {
    it(`renders the Java-owned ${name} contract`, () => {
      expect(Object.values(SERVER_TOOL_NAMES)).toContain(name);
      expect(fixture[name]).toHaveProperty("input");
      expect(fixture[name]).toHaveProperty("output");
    });
  }
});
