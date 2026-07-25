import { z } from "zod";

const validationSchema = z.object({
  success: z.boolean(),
  error: z.string().optional(),
});

export const workflowToolContracts = {
  generate_sql: {
    input: z.object({
      userQuestion: z.string().min(1),
      previousValidationError: z.string().optional(),
      schemaHints: z
        .array(
          z.object({
            database: z.string(),
            table: z.string(),
            columns: z.array(z.object({ name: z.string(), type: z.string() })),
            primaryKey: z.string().optional(),
            partitionBy: z.string().optional(),
            engine: z.string().optional(),
            sortingKey: z.string().optional(),
          })
        )
        .optional(),
      context: z
        .object({
          database: z.string().optional(),
          clickHouseUser: z.string().optional(),
        })
        .optional(),
    }),
    output: z.object({
      sql: z.string(),
      notes: z.string(),
      assumptions: z.array(z.string()),
      needs_clarification: z.boolean(),
      questions: z.array(z.string()),
      validation: validationSchema.optional(),
    }),
  },
  optimize_sql: {
    input: z.object({
      sql: z.string().min(1),
      goal: z.enum(["latency", "memory", "bytes", "dashboard", "other"]).optional(),
      evidence: z.record(z.string(), z.unknown()),
    }),
    output: z.object({
      original_sql: z.string(),
      optimized_sql: z.string(),
      goal: z.enum(["latency", "memory", "bytes", "dashboard", "other"]),
      changes: z.array(z.string()),
      assumptions: z.array(z.string()),
      validation: validationSchema,
    }),
  },
  generate_visualization: {
    input: z.object({
      userQuestion: z.string().min(1),
      sql: z.string().min(1),
    }),
    output: z.object({
      type: z.enum(["line", "bar", "area", "pie", "table", "none"]),
      titleOption: z
        .object({
          title: z.string(),
          align: z.enum(["left", "center", "right"]),
        })
        .optional(),
      width: z.number().min(1).max(12),
      legendOption: z.object({
        placement: z.enum(["none", "bottom", "right", "inside"]),
        values: z.array(z.enum(["min", "max", "sum", "avg", "count"])),
      }),
      valueFormat: z
        .enum([
          "short_number",
          "comma_number",
          "binary_size",
          "percentage",
          "millisecond",
          "microsecond",
        ])
        .optional(),
      datasource: z.object({ sql: z.string() }),
    }),
  },
  search_file: {
    input: z.object({
      query: z.string().min(1),
      glob: z.string().optional(),
      limit: z.number().int().positive().max(100).optional(),
    }),
    output: z.union([
      z.object({
        matches: z.array(
          z.object({
            path: z.string(),
            line: z.number().int().positive(),
            snippet: z.string(),
          })
        ),
        hasMore: z.boolean(),
      }),
      z.object({ error: z.string() }),
    ]),
  },
  read_file: {
    input: z.object({
      path: z.string().min(1),
      startLine: z.number().int().positive().optional(),
      endLine: z.number().int().positive().optional(),
    }),
    output: z.union([
      z.object({
        path: z.string(),
        startLine: z.number().int().positive(),
        endLine: z.number().int().nonnegative(),
        totalLines: z.number().int().nonnegative(),
        content: z.string(),
        truncated: z.boolean(),
        hasPrevious: z.boolean(),
        hasNext: z.boolean(),
      }),
      z.object({ error: z.string() }),
    ]),
  },
} as const;
