import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { CommandManager } from "../commands/command-manager";
import type { SkillDetailResponse } from "./skill-provider";

describe("Java and frontend Skill catalog contract", () => {
  it("consumes the shared detail fixture and derives the same command semantics", () => {
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
    expect(CommandManager.fromSkills([skill]).listCommands()).toEqual([
      {
        name: "catalog-contract",
        description: "Shared catalog contract",
        skillId: "catalog-contract",
        showInSqlEditorQuickAction: true,
        template: "Use the `catalog-contract` skill for this request: $ARGUMENTS",
      },
    ]);
  });
});
