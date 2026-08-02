import type { PanelDescriptor } from "@/components/shared/dashboard/dashboard-model";

const PROFILE_EVENT_COLUMN = /\bProfileEvent_([A-Za-z0-9_]+)\b/g;

export function filterUnsupportedNodeMetrics(
  descriptors: PanelDescriptor[],
  availableEvents?: ReadonlySet<string>
): PanelDescriptor[] {
  if (!availableEvents || availableEvents.size === 0) return descriptors;
  return descriptors.filter((descriptor) => {
    const requiredEvents = Array.from(
      descriptor.datasource.sql.matchAll(PROFILE_EVENT_COLUMN),
      (match) => match[1]
    );
    return requiredEvents.every((event) => availableEvents.has(event));
  });
}
