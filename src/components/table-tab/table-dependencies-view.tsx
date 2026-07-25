import { DependencyView } from "@/components/dependency-view/dependency-view";
import { forwardRef, useImperativeHandle } from "react";
import type { RefreshableTabViewRef } from "./table-tab";

export interface TableDependenciesViewProps {
  database: string;
  table: string;
  autoLoad?: boolean;
}

export const TableDependenciesView = forwardRef<RefreshableTabViewRef, TableDependenciesViewProps>(
  ({ database, table, autoLoad }, ref) => {
    // Expose refresh capability (though dependencies are loaded from metadata, not refreshed independently)
    useImperativeHandle(ref, () => ({
      refresh: () => {
        // Dependencies are loaded from metadata, so refresh would require reloading the entire schema
        // For now, this is a no-op as the parent will handle schema refresh
      },
      supportsTimeSpanSelector: false,
    }));

    // Only render if autoLoad is true
    if (!autoLoad) {
      return null;
    }

    return (
      <div className="h-full w-full">
        <DependencyView database={database} table={table} />
      </div>
    );
  }
);

TableDependenciesView.displayName = "TableDependenciesView";
