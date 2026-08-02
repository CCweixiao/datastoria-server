import { ErrorCode } from "@/lib/clickhouse/clickhouse-error-parser";
import { describe, expect, it } from "vitest";
import {
  areRefreshOptionsEqual,
  normalizeRefreshOptions,
  resolveDashboardErrorCode,
} from "../dashboard-visualization-panel";

describe("dashboard-visualization-panel refresh option helpers", () => {
  it("normalizes an empty filter expression to the default true predicate", () => {
    expect(normalizeRefreshOptions({})).toEqual({ filterExpression: "1=1" });
    expect(normalizeRefreshOptions({ timeSpan: undefined })).toEqual({ filterExpression: "1=1" });
  });

  it("treats undefined and default filter expressions as the same refresh state", () => {
    expect(areRefreshOptionsEqual({}, { filterExpression: "1=1" })).toBe(true);
    expect(areRefreshOptionsEqual({ timeSpan: undefined }, { filterExpression: "1=1" })).toBe(true);
  });
});

describe("dashboard error code resolution", () => {
  it("uses the ClickHouse response header when available", () => {
    expect(resolveDashboardErrorCode("60", "request failed")).toBe(ErrorCode.UNKNOWN_TABLE);
  });

  it("recognizes UNKNOWN_TABLE from a streamed JSON exception body", () => {
    const body = JSON.stringify({
      exception:
        "Code: 60. DB::Exception: Unknown table expression identifier 'system.projections' (UNKNOWN_TABLE)",
    });
    expect(resolveDashboardErrorCode(undefined, body)).toBe(ErrorCode.UNKNOWN_TABLE);
  });

  it("recognizes missing privileges from diagnostic data", () => {
    expect(
      resolveDashboardErrorCode(undefined, "request failed", {
        exception: "Code: 497. DB::Exception: Not enough privileges (NOT_ENOUGH_PRIVILEGES)",
      })
    ).toBe(ErrorCode.NOT_ENOUGH_PRIVILEGES);
  });
});
