import { describe, expect, it } from "vitest";
import { nodeZkMetricsDashboard } from "../dashboards/node-zk-metrics";
import { nodeCpuMetrics } from "./cpu";
import { nodeInsertMetrics } from "./insert";
import { nodeIoMetrics } from "./io";
import { nodeMemoryMetrics } from "./memory";
import { nodeMergeMetrics } from "./merge";
import { nodeQueryMetrics } from "./query";
import { nodeSystemMetrics } from "./system";

const groups = {
  cpu: nodeCpuMetrics,
  io: nodeIoMetrics,
  memory: nodeMemoryMetrics,
  query: nodeQueryMetrics,
  insert: nodeInsertMetrics,
  merge: nodeMergeMetrics,
  system: nodeSystemMetrics,
};

describe("node metric groups", () => {
  it("preserves all 19 former Node Metrics panels in focused groups", () => {
    expect(
      Object.fromEntries(Object.entries(groups).map(([name, panels]) => [name, panels.length]))
    ).toEqual({ cpu: 4, io: 3, memory: 2, query: 4, insert: 1, merge: 3, system: 2 });
    expect(Object.values(groups).flat()).toHaveLength(19);
  });

  it("keeps every metric node-local and documented", () => {
    for (const panel of Object.values(groups).flat()) {
      expect(panel.datasource.sql).not.toContain("clusterAllReplicas");
      expect(panel.titleOption?.descriptionKey).toBeDefined();
    }
  });

  it("documents every node ZooKeeper metric", () => {
    expect(nodeZkMetricsDashboard).toHaveLength(22);
    for (const panel of nodeZkMetricsDashboard) {
      expect(panel.titleOption?.descriptionKey).toBeDefined();
    }
  });
});
