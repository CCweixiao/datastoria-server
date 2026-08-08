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

  it("translates the applicant interrupt action using the active locale", () => {
    expect(translate("en", "approval.interrupt")).toBe("Interrupt work order");
    expect(translate("zh-CN", "approval.interrupt")).toBe("中断工单");
    expect(translate("en", "approval.status.CANCELLED")).toBe("Interrupted");
    expect(translate("zh-CN", "approval.status.CANCELLED")).toBe("已中断");
  });

  it("translates approval filters and SQL editing guidance using the active locale", () => {
    expect(translate("en", "approval.filter.allStatuses")).toBe("All statuses");
    expect(translate("zh-CN", "approval.filter.keyword")).toContain("申请人");
    expect(translate("en", "approval.filter.dateRange.placeholder")).toBe(
      "Select start and end dates"
    );
    expect(translate("zh-CN", "approval.filter.dateRange")).toBe("创建日期范围");
    expect(translate("en", "approval.filter.clearStatus")).toBe("Clear status");
    expect(translate("zh-CN", "approval.filter.clearDateRange")).toBe("清空日期范围");
    expect(translate("en", "approval.type.editTitle")).toBe("Edit work order type");
    expect(translate("zh-CN", "approval.type.rules.format")).toBe("格式化 JSON");
    expect(translate("en", "approval.filter.keyword")).toBe(
      "Search ID, title, summary, applicant…"
    );
    expect(translate("zh-CN", "approval.filter.keyword")).toBe("搜索工单号、标题、摘要、申请人…");
    expect(translate("en", "approval.sql.edit")).toBe("Edit SQL");
    expect(translate("zh-CN", "approval.sql.edit")).toBe("编辑 SQL");
    expect(translate("en", "approval.sql.editNotice")).toContain("Draft");
    expect(translate("zh-CN", "approval.sql.editNotice")).toContain("草稿");
  });

  it("translates approval table actions and audit timeline events in both catalogs", () => {
    expect(translate("en", "approval.viewDetails")).toBe("View");
    expect(translate("zh-CN", "approval.viewDetails")).toBe("查看");
    expect(translate("en", "approval.timeline.event.EXECUTION_SUCCEEDED")).toBe(
      "Execution succeeded"
    );
    expect(translate("zh-CN", "approval.timeline.event.EXECUTION_SUCCEEDED")).toBe("执行成功");
    expect(translate("en", "approval.timeline.message.manualExecutionStarted")).toContain(
      "manual DDL execution"
    );
    expect(translate("zh-CN", "approval.timeline.message.manualExecutionStarted")).toContain(
      "手工执行 DDL"
    );
  });

  it("translates destructive approval actions and cascade warnings in both catalogs", () => {
    expect(translate("en", "approval.delete")).toBe("Delete");
    expect(translate("zh-CN", "approval.delete")).toBe("删除");
    expect(translate("en", "approval.delete.runningDisabled")).toContain("running");
    expect(translate("zh-CN", "approval.delete.runningDisabled")).toContain("执行中");
    expect(translate("en", "approval.delete.cascadeNotice")).toContain("execution records");
    expect(translate("zh-CN", "approval.delete.cascadeNotice")).toContain("执行记录");
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
