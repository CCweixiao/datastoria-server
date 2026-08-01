import { backendApiFetch, backendApiHeaders, backendApiUrl } from "@/lib/backend-api";
import { listUserState, putUserState } from "@/lib/user-state-client";
import type { ConnectionConfig } from "./connection-config";

export const ConnectionChangeType = {
  ADD: 0,
  MODIFY: 1,
  REMOVE: 2,
} as const;

export type ConnectionChangeTypeValue =
  (typeof ConnectionChangeType)[keyof typeof ConnectionChangeType];

export interface ConnectionChangeEventArgs {
  type: ConnectionChangeTypeValue;
  beforeChange: ConnectionConfig | null;
  afterChange: ConnectionConfig | null;
}

const apiUrl = backendApiUrl;

type ServerConnection = {
  id: string;
  name: string;
  url: string;
  username: string;
  cluster?: string | null;
  remark?: string | null;
  enabled: boolean;
  revision: number;
};

function headers(extra?: HeadersInit): HeadersInit {
  return backendApiHeaders(extra);
}

function fromServer(connection: ServerConnection): ConnectionConfig {
  return {
    id: connection.id,
    revision: connection.revision,
    name: connection.name,
    url: connection.url,
    user: connection.username,
    password: "",
    cluster: connection.cluster ?? "",
    remark: connection.remark ?? "",
    editable: true,
  };
}

export class ConnectionManager {
  private static instance: ConnectionManager;

  public static getInstance(): ConnectionManager {
    return this.instance || (this.instance = new this());
  }

  private connectionMap = new Map<string, ConnectionConfig>();
  private connectionArray: ConnectionConfig[] = [];
  private selectedName: string | undefined;
  private readonly hydration: Promise<void>;

  constructor() {
    this.hydration = this.reload();
  }

  async ready(): Promise<void> {
    await this.hydration;
  }

  private async reload(): Promise<void> {
    const [response, selection] = await Promise.all([
      backendApiFetch(apiUrl("/api/connections"), { headers: headers() }),
      listUserState<string>("connection-selection").catch(() => []),
    ]);
    if (!response.ok) {
      throw new Error(`Failed to load ClickHouse connections: ${response.status}`);
    }
    const connections = (await response.json()) as ServerConnection[];
    this.connectionArray = connections.map(fromServer).sort((a, b) => a.name.localeCompare(b.name));
    this.connectionMap = new Map(
      this.connectionArray.map((connection) => [connection.name, connection])
    );
    const selected = selection.find((entry) => entry.key === "current")?.value;
    this.selectedName = selected && this.connectionMap.has(selected) ? selected : undefined;
  }

  getConnections(): ConnectionConfig[] {
    return this.connectionArray;
  }

  contains(name: string): boolean {
    return this.connectionMap.has(name);
  }

  async add(connection: ConnectionConfig): Promise<ConnectionChangeEventArgs> {
    const response = await backendApiFetch(apiUrl("/api/connections"), {
      method: "POST",
      headers: headers({ "Content-Type": "application/json" }),
      body: JSON.stringify({
        name: connection.name,
        url: connection.url,
        username: connection.user,
        password: connection.password,
        cluster: connection.cluster || null,
        remark: connection.remark?.trim() || null,
        enabled: true,
      }),
    });
    if (!response.ok) {
      throw new Error(`Failed to create ClickHouse connection: ${response.status}`);
    }
    const saved = fromServer((await response.json()) as ServerConnection);
    this.connectionArray.push(saved);
    this.connectionArray.sort((a, b) => a.name.localeCompare(b.name));
    this.connectionMap.set(saved.name, saved);
    return { type: ConnectionChangeType.ADD, beforeChange: null, afterChange: saved };
  }

  async replace(name: string, newConnection: ConnectionConfig): Promise<ConnectionChangeEventArgs> {
    const existing = this.connectionMap.get(name);
    if (!existing?.id) {
      return this.add(newConnection);
    }
    const response = await backendApiFetch(apiUrl(`/api/connections/${existing.id}`), {
      method: "PUT",
      headers: headers({
        "Content-Type": "application/json",
        "If-Match": `"${existing.revision ?? 0}"`,
      }),
      body: JSON.stringify({
        name: newConnection.name,
        url: newConnection.url,
        username: newConnection.user,
        ...(newConnection.password ? { password: newConnection.password } : {}),
        cluster: newConnection.cluster || null,
        remark: newConnection.remark?.trim() || null,
        enabled: true,
      }),
    });
    if (!response.ok) {
      throw new Error(`Failed to update ClickHouse connection: ${response.status}`);
    }
    const saved = fromServer((await response.json()) as ServerConnection);
    this.connectionArray = this.connectionArray
      .filter((connection) => connection.name !== name)
      .concat(saved)
      .sort((a, b) => a.name.localeCompare(b.name));
    this.connectionMap.delete(name);
    this.connectionMap.set(saved.name, saved);
    return { type: ConnectionChangeType.MODIFY, beforeChange: existing, afterChange: saved };
  }

  async remove(name: string): Promise<ConnectionChangeEventArgs> {
    const existing = this.connectionMap.get(name) ?? null;
    if (!existing?.id) {
      return { type: ConnectionChangeType.REMOVE, beforeChange: existing, afterChange: null };
    }
    const response = await backendApiFetch(apiUrl(`/api/connections/${existing.id}`), {
      method: "DELETE",
      headers: headers({ "If-Match": `"${existing.revision ?? 0}"` }),
    });
    if (!response.ok) {
      throw new Error(`Failed to delete ClickHouse connection: ${response.status}`);
    }
    this.connectionArray = this.connectionArray.filter((connection) => connection.name !== name);
    this.connectionMap.delete(name);
    if (this.selectedName === name) {
      this.selectedName = undefined;
    }
    return { type: ConnectionChangeType.REMOVE, beforeChange: existing, afterChange: null };
  }

  first(): ConnectionConfig | null {
    return this.connectionArray[0] ?? null;
  }

  saveLastSelected(name: string | undefined): void {
    this.selectedName = name;
    void putUserState("connection-selection", "current", name ?? null).catch((error) =>
      console.error("Failed to persist selected connection:", error)
    );
  }

  getLastSelectedOrFirst(): ConnectionConfig | null {
    return (
      (this.selectedName ? this.connectionMap.get(this.selectedName) : undefined) ?? this.first()
    );
  }
}
