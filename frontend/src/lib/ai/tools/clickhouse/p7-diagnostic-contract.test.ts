import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { ClickHouseTools } from "./clickhouse-tools";

type Schema = {
  safeParse: (value: unknown) => { success: boolean };
};

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
    it(`accepts the shared ${name} input and output`, () => {
      const tool = ClickHouseTools[name] as unknown as {
        inputSchema: Schema;
        outputSchema: Schema;
      };
      expect(tool.inputSchema.safeParse(fixture[name].input).success).toBe(true);
      expect(tool.outputSchema.safeParse(fixture[name].output).success).toBe(true);
    });
  }
});
