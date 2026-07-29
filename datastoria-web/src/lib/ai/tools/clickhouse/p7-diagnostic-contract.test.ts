import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { CLICKHOUSE_TOOL_NAMES } from "./clickhouse-tools";

describe("P7 Java/frontend diagnostic tool contract", () => {
  const fixture = JSON.parse(
    readFileSync(
      resolve(process.cwd(), "../docs/fixtures/tools/p7-diagnostic-contract.json"),
      "utf8"
    )
  );

  for (const name of [
    "search_query_log",
    "collect_cluster_status",
    "collect_sql_optimization_evidence",
    "collect_rca_evidence",
  ] as const) {
    it(`renders the Java-owned ${name} contract`, () => {
      expect(Object.values(CLICKHOUSE_TOOL_NAMES)).toContain(name);
      expect(fixture[name]).toHaveProperty("input");
      expect(fixture[name]).toHaveProperty("output");
    });
  }
});
