import { describe, expect, it } from "vitest";
import { normalizeLocale, resolveLocale, translate } from "./i18n";

describe("application i18n", () => {
  it("normalizes Chinese language tags", () => {
    expect(normalizeLocale("zh-TW")).toBe("zh-CN");
    expect(normalizeLocale("en-US")).toBe("en");
  });

  it("uses the browser language only for the system preference", () => {
    expect(resolveLocale("system", "zh-CN")).toBe("zh-CN");
    expect(resolveLocale("en", "zh-CN")).toBe("en");
  });

  it("translates the same message key in both catalogs", () => {
    expect(translate("en", "settings.title")).toBe("Settings");
    expect(translate("zh-CN", "settings.title")).toBe("设置");
  });

  it("translates the current chat cluster badge using the active locale", () => {
    expect(translate("en", "chat.currentCluster")).toBe("Current");
    expect(translate("zh-CN", "chat.currentCluster")).toBe("当前");
  });

  it("translates cluster monitoring titles and summaries using the active locale", () => {
    expect(translate("en", "monitor.cluster.mergeSourceParts.title")).toBe("Merge Source Parts");
    expect(translate("zh-CN", "monitor.cluster.mergeSourceParts.title")).toBe("合并源 Part 数");
    expect(translate("en", "monitor.cluster.mergeSourceParts.description")).toContain(
      "source parts"
    );
    expect(translate("zh-CN", "monitor.cluster.mergeSourceParts.description")).toContain("源 Part");
  });

  it("translates node monitoring groups and summaries using the active locale", () => {
    expect(translate("en", "monitor.node.group.cpu")).toBe("Node CPU");
    expect(translate("zh-CN", "monitor.node.group.cpu")).toBe("节点 CPU");
    expect(translate("en", "monitor.node.group.memoryAndIo")).toBe("Node Memory & IO");
    expect(translate("zh-CN", "monitor.node.group.memoryAndIo")).toBe("节点内存与 IO");
    expect(translate("en", "monitor.node.cpuUsage.description")).toContain("this node");
    expect(translate("zh-CN", "monitor.node.cpuUsage.description")).toContain("当前节点");
  });

  it("translates dashboard capability errors using the active locale", () => {
    expect(translate("en", "monitor.table.projections.unsupported")).toContain(
      "does not support Projection monitoring"
    );
    expect(translate("zh-CN", "monitor.table.projections.unsupported")).toContain(
      "不支持 Projection 监控"
    );
    expect(translate("en", "dashboard.error.notEnoughPrivileges")).toContain("privileges");
    expect(translate("zh-CN", "dashboard.error.notEnoughPrivileges")).toContain("权限不足");
  });
});
