import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { ClickHouseTools } from "./clickhouse-tools";

type Schema = {
  safeParse: (value: unknown) => { success: boolean };
};

describe("P6 Java/frontend readonly tool contract", () => {
  const contract = JSON.parse(
    readFileSync(resolve(process.cwd(), "../docs/fixtures/tools/p6-readonly-contract.json"), "utf8")
  );

  for (const name of ["get_tables", "explore_schema", "validate_sql"] as const) {
    it(`accepts the shared ${name} input and output`, () => {
      const tool = ClickHouseTools[name] as unknown as {
        inputSchema: Schema;
        outputSchema: Schema;
      };

      expect(tool.inputSchema.safeParse(contract[name].input).success).toBe(true);
      expect(tool.outputSchema.safeParse(contract[name].output).success).toBe(true);
    });
  }
});
