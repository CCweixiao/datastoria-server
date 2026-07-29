import type { Mention, MentionMetadata } from "@/lib/ai/ai-types";
import type { Connection } from "@/lib/connection/connection";

const INLINE_CODE_TOKEN_REGEX = /`([^`\n]+)`(?=[\s?!.,;:)\]}]|$)/g;
const DATABASE_COMMENT_MAX_LENGTH = 200;

function normalizeComment(comment: string): string {
  const normalized = comment.replace(/\s+/g, " ").trim();
  if (normalized.length <= DATABASE_COMMENT_MAX_LENGTH) {
    return normalized;
  }

  return `${normalized.slice(0, DATABASE_COMMENT_MAX_LENGTH - 1).trimEnd()}…`;
}

export class MentionContext {
  static toMetadata(
    text: string,
    connection: Pick<Connection, "metadata">
  ): MentionMetadata | undefined {
    const mentions: Mention[] = [];
    const seen = new Set<string>();
    const tableNames = connection.metadata.tableNames;
    const databaseNames = connection.metadata.databaseNames;
    const settingsByName = connection.metadata.clickhouseSettings;

    for (const match of text.matchAll(INLINE_CODE_TOKEN_REGEX)) {
      const token = match[1]?.trim();
      if (!token) {
        continue;
      }

      if (tableNames?.has(token)) {
        const tableInfo = tableNames.get(token);
        const engine = tableInfo?.engine?.trim();
        if (engine) {
          const key = `table:${token}`;
          if (!seen.has(key)) {
            mentions.push({ kind: "table", name: token, engine });
            seen.add(key);
          }
        }
        continue;
      }

      if (settingsByName?.has(token)) {
        const settingInfo = settingsByName.get(token);
        const type = settingInfo?.type?.trim();
        if (type) {
          const key = `setting:${token}`;
          if (!seen.has(key)) {
            mentions.push({ kind: "setting", name: token, type });
            seen.add(key);
          }
        }
        continue;
      }

      if (databaseNames?.has(token)) {
        const databaseInfo = databaseNames.get(token);
        const engine = databaseInfo?.engine?.trim();
        if (engine) {
          const key = `database:${token}`;
          if (!seen.has(key)) {
            const mention: Mention = { kind: "database", name: token, engine };
            const comment =
              token !== "system" && typeof databaseInfo?.comment === "string"
                ? normalizeComment(databaseInfo.comment)
                : undefined;
            if (comment) {
              mention.comment = comment;
            }
            mentions.push(mention);
            seen.add(key);
          }
        }
      }
    }

    return mentions.length > 0 ? { version: 1, mentions } : undefined;
  }
}
