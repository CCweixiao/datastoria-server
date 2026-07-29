import { loadEffectiveConfiguration, saveConfiguration } from "@/lib/configuration-client";
import type { QueryContext } from "./query-context";

const CONFIG_KEY = "settings.query-context";

class QueryContextManager {
  private static instance: QueryContextManager;
  private context: Partial<QueryContext> = {};

  private constructor() {
    void loadEffectiveConfiguration()
      .then((configuration) => {
        const stored = configuration.entries[CONFIG_KEY];
        this.context = stored ? (JSON.parse(stored) as Partial<QueryContext>) : {};
      })
      .catch((error) => console.error("Failed to load query context:", error));
  }

  public static getInstance(): QueryContextManager {
    if (!QueryContextManager.instance) {
      QueryContextManager.instance = new QueryContextManager();
    }
    return QueryContextManager.instance;
  }

  public getContext(): QueryContext {
    return this.getStoredContext();
  }

  public getStoredContext(): Partial<QueryContext> {
    // Get stored context without defaults (for editing)
    return { ...this.context };
  }

  public setContext(context: Partial<QueryContext>): void {
    // Save exactly what is passed, without merging with defaults
    // Defaults will be applied when reading via getContext()
    this.context = { ...context };
    void saveConfiguration(CONFIG_KEY, this.context).catch((error) =>
      console.error("Failed to save query context:", error)
    );
  }

  public updateContext(updates: Partial<QueryContext>): void {
    // Get stored context without defaults, merge updates, then save
    const stored = this.getStoredContext();
    this.setContext({ ...stored, ...updates });
  }
}

export { QueryContextManager };
