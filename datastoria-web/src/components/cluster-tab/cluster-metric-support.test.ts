import type { TimeseriesDescriptor } from "@/components/shared/dashboard/dashboard-model";
import { describe, expect, it } from "vitest";
import {
  filterUnsupportedProfileEventMetrics,
  requiredProfileEvents,
  supportsRequiredProfileEvents,
} from "./cluster-metric-support";

function panel(sql: string): TimeseriesDescriptor {
  return { type: "line", datasource: { sql } };
}

describe("cluster metric support", () => {
  it("extracts unique ProfileEvent dependencies from SQL", () => {
    expect(
      requiredProfileEvents(
        "SELECT ProfileEvent_Query, ProfileEvent_Query, ProfileEvent_FailedQuery FROM system.metric_log"
      )
    ).toEqual(["Query", "FailedQuery"]);
  });

  it("keeps metrics when capability discovery is unavailable", () => {
    const descriptor = panel("SELECT ProfileEvent_Query FROM system.metric_log");
    expect(supportsRequiredProfileEvents(descriptor)).toBe(true);
    expect(supportsRequiredProfileEvents(descriptor, new Set())).toBe(true);
  });

  it("filters only metrics with explicitly unsupported events", () => {
    const supported = panel("SELECT ProfileEvent_Query FROM system.metric_log");
    const unsupported = panel("SELECT ProfileEvent_NewEvent FROM system.metric_log");
    const eventIndependent = panel("SELECT count() FROM system.processes");

    expect(
      filterUnsupportedProfileEventMetrics(
        [supported, unsupported, eventIndependent],
        new Set(["Query"])
      )
    ).toEqual([supported, eventIndependent]);
  });
});
