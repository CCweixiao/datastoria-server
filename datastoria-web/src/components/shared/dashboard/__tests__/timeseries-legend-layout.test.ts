import { describe, expect, it } from "vitest";
import { getTimeseriesLegendPresentation } from "../timeseries-legend-layout";

describe("getTimeseriesLegendPresentation", () => {
  it.each([
    [undefined, "inside"],
    ["inside", "inside"],
    ["bottom", "bottom"],
    ["right", "right"],
    ["none", "hidden"],
  ] as const)("maps %s placement to %s presentation", (placement, expected) => {
    expect(getTimeseriesLegendPresentation(placement)).toBe(expected);
  });
});
