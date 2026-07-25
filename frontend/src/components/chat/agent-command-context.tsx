"use client";

import type { CommandDetail } from "@/lib/ai/commands/command-manager";
import { backendApiFetch, backendApiHeaders, backendApiUrl } from "@/lib/backend-api";
import React, { createContext, useContext, useEffect, useMemo, useState } from "react";

interface AgentCommandContextValue {
  commands: CommandDetail[];
  commandsByName: Map<string, CommandDetail>;
  loading: boolean;
}

const AgentCommandContext = createContext<AgentCommandContextValue>({
  commands: [],
  commandsByName: new Map<string, CommandDetail>(),
  loading: false,
});

export function AgentCommandProvider({ children }: { children: React.ReactNode }) {
  const [commands, setCommands] = useState<CommandDetail[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const controller = new AbortController();

    setLoading(true);

    backendApiFetch(backendApiUrl("/api/ai/commands"), {
      signal: controller.signal,
      headers: backendApiHeaders(),
    })
      .then((res) => {
        if (!res.ok) {
          throw new Error(`HTTP ${res.status}`);
        }
        return res.json() as Promise<CommandDetail[]>;
      })
      .then((data) => {
        if (controller.signal.aborted) return;
        setCommands(data);
        setLoading(false);
      })
      .catch(() => {
        if (controller.signal.aborted) return;
        setCommands([]);
        setLoading(false);
      });

    return () => {
      controller.abort();
    };
  }, []);

  const value = useMemo<AgentCommandContextValue>(() => {
    return {
      commands,
      commandsByName: new Map(commands.map((command) => [command.name, command])),
      loading,
    };
  }, [commands, loading]);

  return <AgentCommandContext.Provider value={value}>{children}</AgentCommandContext.Provider>;
}

export function useAgentCommands() {
  return useContext(AgentCommandContext);
}
