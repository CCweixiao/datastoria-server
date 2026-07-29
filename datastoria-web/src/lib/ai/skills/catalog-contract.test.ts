import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import type { SkillDetailResponse } from "./skill-provider";

describe("Java and frontend Skill catalog contract", () => {
  it("consumes the shared detail fixture as display-only data", () => {
    const fixturePath = resolve(process.cwd(), "../docs/fixtures/skills/catalog-detail.json");
    const skill = JSON.parse(readFileSync(fixturePath, "utf8")) as SkillDetailResponse;

    expect(skill).toMatchObject({
      source: "database",
      status: "available",
      state: "published",
      scope: "self",
      url: "https://example.com/catalog-contract",
      requiredTools: ["execute_sql"],
      resourcePaths: ["references/rules.md"],
    });
    expect(skill.name).toBe("catalog-contract");
    expect(skill.description).toBe("Shared catalog contract");
    expect(skill.showInSqlEditorQuickAction).toBe(true);
  });
});
