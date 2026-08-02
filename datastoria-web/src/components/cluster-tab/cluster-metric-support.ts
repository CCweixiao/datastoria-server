import type { PanelDescriptor } from "@/components/shared/dashboard/dashboard-model";

const PROFILE_EVENT_COLUMN = /\bProfileEvent_([A-Za-z0-9_]+)\b/g;

export function requiredProfileEvents(sql: string): string[] {
  return Array.from(sql.matchAll(PROFILE_EVENT_COLUMN), (match) => match[1]).filter(
    (event, index, events) => events.indexOf(event) === index
  );
}

export function supportsRequiredProfileEvents(
  descriptor: PanelDescriptor,
  availableEvents?: ReadonlySet<string>
): boolean {
  // An empty set can also mean metadata discovery failed, so preserve the existing behavior.
  if (!availableEvents || availableEvents.size === 0) return true;
  return requiredProfileEvents(descriptor.datasource.sql).every((event) =>
    availableEvents.has(event)
  );
}

export function filterUnsupportedProfileEventMetrics(
  descriptors: PanelDescriptor[],
  availableEvents?: ReadonlySet<string>
): PanelDescriptor[] {
  return descriptors.filter((descriptor) =>
    supportsRequiredProfileEvents(descriptor, availableEvents)
  );
}
