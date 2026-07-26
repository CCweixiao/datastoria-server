import { deleteUserState, listUserState, putUserState } from "@/lib/user-state-client";
import type { ResponsiveLayouts } from "react-grid-layout";

export const STORAGE_KEY_PREFIX = "dashboard-layout:";
const CURRENT_VERSION = 1;
const layouts = new Map<string, SavedLayout>();

export interface SavedLayout {
  version: number;
  dashboardId: string;
  layouts: ResponsiveLayouts;
  updatedAt: string;
}

if (typeof window !== "undefined") {
  void listUserState<SavedLayout>("dashboard-layout")
    .then((entries) => entries.forEach((entry) => layouts.set(entry.key, entry.value)))
    .catch((error) => console.error("Failed to load dashboard layouts:", error));
}

function sectionKey(dashboardId: string, sectionIndex: number): string {
  return `${dashboardId}-section-${sectionIndex}`;
}

function save(key: string, data: SavedLayout): void {
  layouts.set(key, data);
  void putUserState("dashboard-layout", key, data).catch((error) =>
    console.error("Failed to save dashboard layout:", error)
  );
}

function load(key: string): ResponsiveLayouts | null {
  const data = layouts.get(key);
  return data?.version === CURRENT_VERSION ? data.layouts : null;
}

function clear(key: string): void {
  layouts.delete(key);
  void deleteUserState("dashboard-layout", key).catch((error) =>
    console.error("Failed to clear dashboard layout:", error)
  );
}

export function saveSectionLayout(
  dashboardId: string,
  sectionIndex: number,
  responsiveLayouts: ResponsiveLayouts
): void {
  const key = sectionKey(dashboardId, sectionIndex);
  save(key, {
    version: CURRENT_VERSION,
    dashboardId: key,
    layouts: responsiveLayouts,
    updatedAt: new Date().toISOString(),
  });
}

export function loadSectionLayout(
  dashboardId: string,
  sectionIndex: number
): ResponsiveLayouts | null {
  return load(sectionKey(dashboardId, sectionIndex));
}

export function clearSectionLayout(dashboardId: string, sectionIndex: number): void {
  clear(sectionKey(dashboardId, sectionIndex));
}

export function clearAllSectionLayouts(dashboardId: string): void {
  const prefix = `${dashboardId}-section-`;
  [...layouts.keys()].filter((key) => key.startsWith(prefix)).forEach(clear);
}

export function invalidateLegacySectionLayoutKeys(dashboardId: string): void {
  clearAllSectionLayouts(dashboardId);
}

export function saveDashboardLayout(
  dashboardId: string,
  responsiveLayouts: ResponsiveLayouts
): void {
  save(dashboardId, {
    version: CURRENT_VERSION,
    dashboardId,
    layouts: responsiveLayouts,
    updatedAt: new Date().toISOString(),
  });
}

export function loadDashboardLayout(dashboardId: string): ResponsiveLayouts | null {
  return load(dashboardId);
}

export function clearDashboardLayout(dashboardId: string): void {
  clear(dashboardId);
}
