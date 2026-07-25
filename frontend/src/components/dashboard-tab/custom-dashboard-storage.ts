import type {
  Dashboard,
  DashboardFilter,
  DashboardGroup,
  FilterSpec,
  PanelDescriptor,
} from "@/components/shared/dashboard/dashboard-model";
import { deleteUserState, listUserState, putUserState } from "@/lib/user-state-client";

/**
 * Represents a saved custom dashboard configuration
 */
export interface CustomDashboardConfig {
  id: string;
  name: string;
  createdAt: number;
  updatedAt: number;
  filter: DashboardFilter;
  filterSpecs: FilterSpec[];
  panels: (PanelDescriptor | DashboardGroup)[];
}

/**
 * Storage manager for custom dashboards.
 * Persists dashboard configs through the backend user-state API.
 */
export class CustomDashboardStorage {
  private static instance: CustomDashboardStorage;

  static getInstance(): CustomDashboardStorage {
    if (!this.instance) {
      this.instance = new CustomDashboardStorage();
    }
    return this.instance;
  }

  private dashboards = new Map<string, CustomDashboardConfig>();

  private constructor() {
    void listUserState<CustomDashboardConfig>("custom-dashboard")
      .then((entries) => {
        this.dashboards = new Map(entries.map((entry) => [entry.key, entry.value]));
      })
      .catch((error) => console.error("Failed to load custom dashboards:", error));
  }

  /**
   * Get all saved dashboards (metadata only for listing)
   */
  getAll(): CustomDashboardConfig[] {
    return [...this.dashboards.values()].sort((a, b) => b.updatedAt - a.updatedAt);
  }

  /**
   * Get a single dashboard by ID
   */
  get(id: string): CustomDashboardConfig | null {
    return this.dashboards.get(id) ?? null;
  }

  /**
   * Save a dashboard (create or update)
   */
  save(config: CustomDashboardConfig): void {
    const saved = { ...config, updatedAt: Date.now() };
    this.dashboards.set(config.id, saved);
    void putUserState("custom-dashboard", config.id, saved).catch((error) =>
      console.error("Failed to save custom dashboard:", error)
    );
  }

  /**
   * Delete a dashboard by ID
   */
  delete(id: string): void {
    this.dashboards.delete(id);
    void deleteUserState("custom-dashboard", id).catch((error) =>
      console.error("Failed to delete custom dashboard:", error)
    );
  }

  /**
   * Create a new empty dashboard with default structure
   */
  createNew(name: string): CustomDashboardConfig {
    const now = Date.now();
    const defaultSection: DashboardGroup = {
      title: "Default",
      charts: [],
      collapsed: false,
    };
    const config: CustomDashboardConfig = {
      id: `dashboard-${now}-${Math.random().toString(36).slice(2, 8)}`,
      name,
      createdAt: now,
      updatedAt: now,
      filter: {
        showTimeSpanSelector: true,
        showRefresh: true,
      },
      filterSpecs: [],
      panels: [defaultSection],
    };
    this.save(config);
    return config;
  }

  /**
   * Convert a CustomDashboardConfig to a Dashboard model for rendering
   */
  static toDashboard(config: CustomDashboardConfig): Dashboard {
    return {
      name: config.name,
      version: 3,
      filter: config.filter,
      charts: config.panels,
    };
  }
}
