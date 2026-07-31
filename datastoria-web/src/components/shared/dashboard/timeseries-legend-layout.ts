import type { LegendPlacement } from "./dashboard-model";

export type TimeseriesLegendPresentation = "hidden" | "inside" | "bottom" | "right";

export function getTimeseriesLegendPresentation(
  placement: LegendPlacement | undefined
): TimeseriesLegendPresentation {
  if (placement === "none") return "hidden";
  if (placement === "bottom") return "bottom";
  if (placement === "right") return "right";
  return "inside";
}
