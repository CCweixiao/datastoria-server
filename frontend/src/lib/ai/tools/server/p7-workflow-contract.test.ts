import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { workflowToolContracts } from "./workflow-tool-contracts";

describe("P7 Java/frontend workflow tool contract", () => {
  const fixture = JSON.parse(
    readFileSync(resolve(process.cwd(), "../docs/fixtures/tools/p7-workflow-contract.json"), "utf8")
  );

  for (const name of Object.keys(workflowToolContracts) as Array<
    keyof typeof workflowToolContracts
  >) {
    it(`accepts the shared ${name} input and output`, () => {
      expect(workflowToolContracts[name].input.safeParse(fixture[name].input).success).toBe(true);
      expect(workflowToolContracts[name].output.safeParse(fixture[name].output).success).toBe(true);
    });
  }
});
