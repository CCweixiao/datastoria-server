import { describe, expect, it } from "vitest";
import { MentionContext } from "./mention-context";

describe("MentionContext.toMetadata", () => {
  const connection = {
    metadata: {
      tableNames: new Map([
        [
          "system.query_log",
          {
            database: "system",
            table: "query_log",
            engine: "MergeTree",
            columns: [
              { name: "query_id", type: "String" },
              { name: "query", type: "String" },
            ],
          },
        ],
      ]),
      databaseNames: new Map([
        [
          "analytics",
          {
            name: "analytics",
            engine: "Atomic",
            comment: "Analytics database for BI and ad hoc reporting.",
          },
        ],
      ]),
      clickhouseSettings: new Map([
        [
          "max_threads",
          {
            name: "max_threads",
            value: "8",
            changed: false,
            description: "Maximum number of execution threads.",
            min: null,
            max: null,
            readonly: false,
            type: "UInt64",
            source: "settings",
          },
        ],
      ]),
    },
  };

  it("extracts table, database, and setting mentions from inline code tokens", () => {
    const mentionMetadata = MentionContext.toMetadata(
      "Compare `analytics` with `system.query_log` and tune `max_threads`",
      connection as never
    );

    expect(mentionMetadata).toEqual({
      version: 1,
      mentions: [
        {
          kind: "database",
          name: "analytics",
          engine: "Atomic",
          comment: "Analytics database for BI and ad hoc reporting.",
        },
        {
          kind: "table",
          name: "system.query_log",
          engine: "MergeTree",
        },
        {
          kind: "setting",
          name: "max_threads",
          type: "UInt64",
        },
      ],
    });
  });
});
