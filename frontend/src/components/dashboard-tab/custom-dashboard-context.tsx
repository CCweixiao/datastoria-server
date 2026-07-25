"use client";

import type { PanelDescriptor } from "@/components/shared/dashboard/dashboard-model";
import { createContext, useContext } from "react";

export interface CustomDashboardPanelActions {
  onEditPanel: (index: number) => void;
  onDeletePanel: (index: number) => void;
  /** Get the flat index for a panel descriptor */
  getPanelIndex: (descriptor: PanelDescriptor) => number;
}

export const CustomDashboardContext = createContext<CustomDashboardPanelActions | null>(null);

export function useCustomDashboardActions(): CustomDashboardPanelActions | null {
  return useContext(CustomDashboardContext);
}
