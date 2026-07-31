import { describe, expect, it } from "vitest";
import { parseVisualizationDescriptor } from "./message-tool-generate-visualization";

describe("parseVisualizationDescriptor", () => {
  const descriptor = {
    type: "pie",
    legendOption: { placement: "right" },
    datasource: { sql: "SELECT name, count() FROM events GROUP BY name" },
  };

  it("keeps structured tool output", () => {
    expect(parseVisualizationDescriptor(descriptor)).toEqual(descriptor);
  });

  it("parses legacy JSON string tool output without spreading its characters", () => {
    expect(parseVisualizationDescriptor(JSON.stringify(descriptor))).toEqual(descriptor);
  });

  it("rejects invalid string output", () => {
    expect(parseVisualizationDescriptor("not json")).toBeUndefined();
  });
});
