import type { SkillCatalogItem } from "./skill-types";

export type { SkillCatalogItem };

export interface SkillDetailResponse extends SkillCatalogItem {
  /** Server-authorized edit capability for the current identity. */
  canEdit: boolean;
  /**
   * Full SKILL.md content (raw markdown, including frontmatter).
   * The frontend toggle decides whether to render it or show raw.
   */
  content: string;
  /**
   * Flat list of relative paths for all sub-resource files in the skill directory
   * (excluding SKILL.md itself). e.g. ["AGENTS.md", "rules/insert-batch-size.md"]
   * The frontend builds the directory tree from these path segments.
   */
  resourcePaths: string[];
}

export interface SkillResourceResponse {
  content: string;
  source: SkillCatalogItem["source"];
  state?: SkillCatalogItem["state"];
  scope?: SkillCatalogItem["scope"];
  author?: SkillCatalogItem["author"];
  version?: SkillCatalogItem["version"];
}

export function findSkillByLookup(
  skills: SkillCatalogItem[],
  lookup: string
): SkillCatalogItem | null {
  const trimmed = lookup.trim();
  if (!trimmed) return null;

  const direct = skills.find((skill) => skill.id === trimmed || skill.name === trimmed);
  if (direct) return direct;

  const normalized = trimmed.toLowerCase();
  return (
    skills.find(
      (skill) => skill.id.toLowerCase() === normalized || skill.name.toLowerCase() === normalized
    ) ?? null
  );
}
