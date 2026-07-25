import type {
  Dashboard,
  DashboardFilter,
  DashboardGroup,
  FilterSpec,
  PanelDescriptor,
} from "@/components/shared/dashboard/dashboard-model";
import { StorageManager } from "@/lib/storage/storage-manager";

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
 * Persists dashboard configs to localStorage under "custom-dashboards" namespace.
 */
export class CustomDashboardStorage {
  private static instance: CustomDashboardStorage;

  static getInstance(): CustomDashboardStorage {
    if (!this.instance) {
      this.instance = new CustomDashboardStorage();
    }
    return this.instance;
  }

  private getStorage() {
    return StorageManager.getInstance()
      .getStorageProvider()
      .subStorage("custom-dashboards")
      .withCompression(true);
  }

  /**
   * Get all saved dashboards (metadata only for listing)
   */
  getAll(): CustomDashboardConfig[] {
    const stored = this.getStorage().getAsJSON<Record<string, CustomDashboardConfig>>(() => ({}));
    return Object.values(stored).sort((a, b) => b.updatedAt - a.updatedAt);
  }

  /**
   * Get a single dashboard by ID
   */
  get(id: string): CustomDashboardConfig | null {
    const stored = this.getStorage().getAsJSON<Record<string, CustomDashboardConfig>>(() => ({}));
    return stored[id] ?? null;
  }

  /**
   * Save a dashboard (create or update)
   */
  save(config: CustomDashboardConfig): void {
    const stored = this.getStorage().getAsJSON<Record<string, CustomDashboardConfig>>(() => ({}));
    stored[config.id] = { ...config, updatedAt: Date.now() };
    this.getStorage().setJSON(stored);
  }

  /**
   * Delete a dashboard by ID
   */
  delete(id: string): void {
    const stored = this.getStorage().getAsJSON<Record<string, CustomDashboardConfig>>(() => ({}));
    delete stored[id];
    this.getStorage().setJSON(stored);
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
