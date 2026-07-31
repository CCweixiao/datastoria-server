import { describe, expect, it } from "vitest";
import { buildChartAxis, detectChartColumns } from "../chart-axis-utils";

describe("buildChartAxis", () => {
  it("keeps categorical axes in query result order", () => {
    const result = buildChartAxis(
      [
        { service: "api", total: 12 },
        { service: "worker", total: 7 },
      ],
      "service",
      false
    );

    expect(result.values).toEqual(["api", "worker"]);
    expect(result.labels).toEqual(["api", "worker"]);
    expect(result.rowByValue.get("worker")?.total).toBe(7);
  });

  it("normalizes and sorts time axes", () => {
    const result = buildChartAxis(
      [
        { event_time: 200, total: 2 },
        { event_time: 100, total: 1 },
      ],
      "event_time",
      true
    );

    expect(result.values).toEqual([100_000, 200_000]);
    expect(result.labels).toHaveLength(2);
  });

  it("detects a categorical axis with a numeric metric", () => {
    expect(
      detectChartColumns(
        [
          { service: "api", total: 12 },
          { service: "worker", total: 7 },
        ],
        [
          { name: "service", type: "String" },
          { name: "total", type: "UInt64" },
        ]
      )
    ).toEqual({
      axisColumn: "service",
      isTimeAxis: false,
      valueColumns: ["total"],
      labelColumns: [],
    });
  });

  it("recognizes ClickHouse numeric strings as metrics", () => {
    expect(
      detectChartColumns(
        [{ status: "ok", total: "42" }],
        [
          { name: "status", type: "String" },
          { name: "total", type: "Decimal(18, 2)" },
        ]
      ).valueColumns
    ).toEqual(["total"]);
  });

  it("prefers a timestamp axis and keeps categorical columns as series dimensions", () => {
    expect(
      detectChartColumns(
        [{ event_time: "2026-07-31 10:00:00", service: "api", total: 3 }],
        [
          { name: "event_time", type: "DateTime" },
          { name: "service", type: "String" },
          { name: "total", type: "UInt64" },
        ]
      )
    ).toEqual({
      axisColumn: "event_time",
      isTimeAxis: true,
      valueColumns: ["total"],
      labelColumns: ["service"],
    });
  });

  it("does not treat additional timestamp-shaped columns as metrics", () => {
    const result = detectChartColumns([{ event_time: "2026-07-31 10:00:00", total: 3 }], []);

    expect(result.isTimeAxis).toBe(true);
    expect(result.valueColumns).toEqual(["total"]);
  });
});
