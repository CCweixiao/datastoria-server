export interface PanelConstraints {
  minW: number;
  minH: number;
  maxW: number;
  maxH: number;
}

const PANEL_CONSTRAINTS: Record<string, PanelConstraints> = {
  stat: { minW: 3, minH: 2, maxW: 12, maxH: 4 },
  gauge: { minW: 3, minH: 3, maxW: 12, maxH: 8 },
  pie: { minW: 4, minH: 4, maxW: 12, maxH: 10 },
  table: { minW: 6, minH: 3, maxW: 24, maxH: 16 },
  "transpose-table": { minW: 4, minH: 2, maxW: 24, maxH: 12 },
  // Timeseries types
  line: { minW: 6, minH: 3, maxW: 24, maxH: 12 },
  bar: { minW: 6, minH: 3, maxW: 24, maxH: 12 },
  area: { minW: 6, minH: 3, maxW: 24, maxH: 12 },
};

const DEFAULT_CONSTRAINTS: PanelConstraints = {
  minW: 3,
  minH: 2,
  maxW: 24,
  maxH: 12,
};

export function getPanelConstraints(panelType: string): PanelConstraints {
  return PANEL_CONSTRAINTS[panelType] ?? DEFAULT_CONSTRAINTS;
}
