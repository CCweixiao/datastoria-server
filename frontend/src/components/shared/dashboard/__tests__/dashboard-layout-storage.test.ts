import type { LayoutItem } from "react-grid-layout";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  clearAllSectionLayouts,
  clearDashboardLayout,
  loadDashboardLayout,
  loadSectionLayout,
  saveDashboardLayout,
  saveSectionLayout,
} from "../dashboard-layout-storage";

const { putUserState, deleteUserState } = vi.hoisted(() => ({
  putUserState: vi.fn().mockResolvedValue({}),
  deleteUserState: vi.fn().mockResolvedValue(undefined),
}));

vi.mock("@/lib/user-state-client", () => ({
  listUserState: vi.fn().mockResolvedValue([]),
  putUserState,
  deleteUserState,
}));

const sampleLayouts = {
  lg: [{ i: "panel-0", x: 0, y: 0, w: 6, h: 4 }] as LayoutItem[],
  md: [],
  sm: [],
};

describe("dashboard-layout-storage backend persistence", () => {
  beforeEach(() => vi.clearAllMocks());

  it("writes dashboard layouts through the user-state API and keeps a read cache", () => {
    saveDashboardLayout("dashboard-a", sampleLayouts);

    expect(putUserState).toHaveBeenCalledWith(
      "dashboard-layout",
      "dashboard-a",
      expect.objectContaining({ version: 1, dashboardId: "dashboard-a", layouts: sampleLayouts })
    );
    expect(loadDashboardLayout("dashboard-a")).toEqual(sampleLayouts);
  });

  it("writes section layouts with server keys", () => {
    saveSectionLayout("dashboard-b", 2, sampleLayouts);

    expect(putUserState).toHaveBeenCalledWith(
      "dashboard-layout",
      "dashboard-b-section-2",
      expect.objectContaining({ layouts: sampleLayouts })
    );
    expect(loadSectionLayout("dashboard-b", 2)).toEqual(sampleLayouts);
  });

  it("deletes dashboard and section layouts through the backend", () => {
    saveDashboardLayout("dashboard-c", sampleLayouts);
    saveSectionLayout("dashboard-c", 0, sampleLayouts);
    saveSectionLayout("dashboard-c", 1, sampleLayouts);

    clearDashboardLayout("dashboard-c");
    clearAllSectionLayouts("dashboard-c");

    expect(deleteUserState).toHaveBeenCalledWith("dashboard-layout", "dashboard-c");
    expect(deleteUserState).toHaveBeenCalledWith("dashboard-layout", "dashboard-c-section-0");
    expect(deleteUserState).toHaveBeenCalledWith("dashboard-layout", "dashboard-c-section-1");
    expect(loadDashboardLayout("dashboard-c")).toBeNull();
  });
});
