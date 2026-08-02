import type { TimeseriesDescriptor } from "@/components/shared/dashboard/dashboard-model";
import { describe, expect, it } from "vitest";
import { filterUnsupportedNodeMetrics } from "./node-metric-support";

const panel = (sql: string): TimeseriesDescriptor => ({ type: "line", datasource: { sql } });

describe("node metric support", () => {
  it("filters metrics with unavailable ProfileEvents", () => {
    const supported = panel("SELECT ProfileEvent_Query FROM system.metric_log");
    const unavailable = panel("SELECT ProfileEvent_MergeSourceParts FROM system.metric_log");
    expect(filterUnsupportedNodeMetrics([supported, unavailable], new Set(["Query"]))).toEqual([
      supported,
    ]);
  });

  it("preserves existing behavior when discovery is unavailable", () => {
    const descriptor = panel("SELECT ProfileEvent_Query FROM system.metric_log");
    expect(filterUnsupportedNodeMetrics([descriptor], new Set())).toEqual([descriptor]);
  });
});
