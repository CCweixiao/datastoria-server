import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { CLICKHOUSE_TOOL_NAMES } from "./clickhouse-tools";

describe("P6 Java/frontend readonly tool contract", () => {
  const contract = JSON.parse(
    readFileSync(resolve(process.cwd(), "../docs/fixtures/tools/p6-readonly-contract.json"), "utf8")
  );

  for (const name of ["get_tables", "explore_schema", "validate_sql"] as const) {
    it(`renders the Java-owned ${name} contract`, () => {
      expect(Object.values(CLICKHOUSE_TOOL_NAMES)).toContain(name);
      expect(contract[name]).toHaveProperty("input");
      expect(contract[name]).toHaveProperty("output");
    });
  }
});
