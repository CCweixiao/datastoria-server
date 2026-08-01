import { beforeEach, describe, expect, it, vi } from "vitest";
import { QueryHistoryManager } from "./query-history-manager";

const listQueryHistory = vi.fn();
const addQueryHistory = vi.fn();
const deleteQueryHistory = vi.fn();
const clearQueryHistory = vi.fn();

vi.mock("@/lib/query-history-client", () => ({
  listQueryHistory: (...args: unknown[]) => listQueryHistory(...args),
  addQueryHistory: (...args: unknown[]) => addQueryHistory(...args),
  deleteQueryHistory: (...args: unknown[]) => deleteQueryHistory(...args),
  clearQueryHistory: (...args: unknown[]) => clearQueryHistory(...args),
}));

function dto(id: string, sql: string, executedAt: string) {
  return {
    id,
    connectionId: "conn-1",
    connectionName: "Prod",
    rawSql: sql,
    executedAt,
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((resolver) => {
    resolve = resolver;
  });
  return { promise, resolve };
}

describe("QueryHistoryManager", () => {
  let manager: QueryHistoryManager;

  beforeEach(() => {
    // A real EventTarget so addEventListener/dispatchEvent actually deliver events.
    vi.stubGlobal("window", new EventTarget());
    listQueryHistory.mockReset();
    addQueryHistory.mockReset();
    deleteQueryHistory.mockReset();
    clearQueryHistory.mockReset();
    manager = new QueryHistoryManager();
  });

  it("loads entries from the backend mapped to the UI shape", async () => {
    listQueryHistory.mockResolvedValue([
      dto("a", "SELECT 1", "2026-08-01T00:00:00Z"),
      dto("b", "SELECT 2", "2026-07-31T00:00:00Z"),
    ]);

    const entries = await manager.load("conn-1");

    expect(listQueryHistory).toHaveBeenCalledWith("conn-1", undefined);
    expect(entries.map((entry) => entry.id)).toEqual(["a", "b"]);
    expect(entries[0]).toMatchObject({
      rawSQL: "SELECT 1",
      connectionId: "conn-1",
      connectionName: "Prod",
      timestamp: Date.parse("2026-08-01T00:00:00Z"),
    });
  });

  it("saves via the backend and refreshes the active connection", async () => {
    listQueryHistory.mockResolvedValue([dto("a", "SELECT 1", "2026-08-01T00:00:00Z")]);
    await manager.load("conn-1");

    addQueryHistory.mockResolvedValue(dto("a", "SELECT 1", "2026-08-02T00:00:00Z"));
    listQueryHistory.mockResolvedValue([dto("a", "SELECT 1", "2026-08-02T00:00:00Z")]);

    await manager.add({
      rawSQL: "SELECT 1",
      connectionId: "conn-1",
      connectionName: "Prod",
    });

    expect(addQueryHistory).toHaveBeenCalledWith({
      connectionId: "conn-1",
      rawSql: "SELECT 1",
      connectionName: "Prod",
    });
    expect(manager.list().map((entry) => entry.id)).toEqual(["a"]);
  });

  it("does not reload when saving for an inactive connection", async () => {
    listQueryHistory.mockResolvedValue([dto("a", "SELECT 1", "2026-08-01T00:00:00Z")]);
    await manager.load("conn-1");

    addQueryHistory.mockResolvedValue(dto("b", "SELECT 2", "2026-08-01T00:00:00Z"));
    await manager.add({
      rawSQL: "SELECT 2",
      connectionId: "conn-2",
      connectionName: "Other",
    });

    expect(listQueryHistory).toHaveBeenCalledTimes(1);
  });

  it("clears cached entries immediately when switching connections", async () => {
    listQueryHistory.mockResolvedValue([dto("a", "SELECT 1", "2026-08-01T00:00:00Z")]);
    await manager.load("conn-1");

    const nextLoad = deferred<ReturnType<typeof dto>[]>();
    listQueryHistory.mockReturnValue(nextLoad.promise);
    const loading = manager.load("conn-2");

    expect(manager.list()).toEqual([]);
    nextLoad.resolve([{ ...dto("b", "SELECT 2", "2026-08-02T00:00:00Z"), connectionId: "conn-2" }]);
    await loading;
    expect(manager.list().map((entry) => entry.id)).toEqual(["b"]);
  });

  it("ignores a stale response after the active connection changes", async () => {
    const firstLoad = deferred<ReturnType<typeof dto>[]>();
    const secondLoad = deferred<ReturnType<typeof dto>[]>();
    listQueryHistory.mockReturnValueOnce(firstLoad.promise).mockReturnValueOnce(secondLoad.promise);

    const loadingFirst = manager.load("conn-1");
    const loadingSecond = manager.load("conn-2");
    secondLoad.resolve([
      { ...dto("new", "SELECT 2", "2026-08-02T00:00:00Z"), connectionId: "conn-2" },
    ]);
    await loadingSecond;
    firstLoad.resolve([dto("stale", "SELECT 1", "2026-08-01T00:00:00Z")]);
    await loadingFirst;

    expect(manager.list().map((entry) => entry.id)).toEqual(["new"]);
  });

  it("removes an entry by id after the backend confirms deletion", async () => {
    listQueryHistory.mockResolvedValue([
      dto("a", "SELECT 1", "2026-08-01T00:00:00Z"),
      dto("b", "SELECT 2", "2026-07-31T00:00:00Z"),
    ]);
    await manager.load("conn-1");

    deleteQueryHistory.mockResolvedValue(undefined);
    await manager.remove("a");

    expect(deleteQueryHistory).toHaveBeenCalledWith("a");
    expect(manager.list().map((entry) => entry.id)).toEqual(["b"]);
  });

  it("clears the active connection's entries", async () => {
    listQueryHistory.mockResolvedValue([dto("a", "SELECT 1", "2026-08-01T00:00:00Z")]);
    await manager.load("conn-1");

    clearQueryHistory.mockResolvedValue(undefined);
    await manager.clear("conn-1");

    expect(clearQueryHistory).toHaveBeenCalledWith("conn-1");
    expect(manager.list()).toEqual([]);
  });

  it("notifies listeners after mutations", async () => {
    const listener = vi.fn();
    manager.addListener(listener);

    listQueryHistory.mockResolvedValue([]);
    await manager.load("conn-1");

    expect(listener).toHaveBeenCalled();
  });
});
