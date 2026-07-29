import type { Connection } from "@/lib/connection/connection";
import { deleteUserState, listUserState, putUserState } from "@/lib/user-state-client";
import type { Ace } from "ace-builds";
import { builtinSnippet } from "./builtin-snippet";
import type { Snippet } from "./snippet";

export class QuerySnippetManager {
  private static instance: QuerySnippetManager;

  public static getInstance(): QuerySnippetManager {
    return this.instance || (this.instance = new this());
  }

  private readonly snippets: Map<string, Snippet>;
  private snippetCompletionList: Ace.SnippetCompletion[];
  private listeners: Array<() => void> = [];

  private async loadFromBackend(): Promise<void> {
    try {
      const stored = await listUserState<Snippet>("sql-snippet");
      this.snippets.clear();
      for (const entry of stored) {
        this.snippets.set(entry.key, entry.value);
      }
    } catch {
      this.snippets.clear();
    }
    this.snippetCompletionList = this.toCompletion();
    this.notifyListeners();
  }

  constructor() {
    this.snippets = new Map<string, Snippet>();
    this.snippetCompletionList = [];
    void this.loadFromBackend();
  }

  public subscribe(listener: () => void): () => void {
    this.listeners.push(listener);
    return () => {
      this.listeners = this.listeners.filter((l) => l !== listener);
    };
  }

  private notifyListeners() {
    this.listeners.forEach((listener) => listener());
  }

  public getSnippets(): Snippet[] {
    return Array.from(this.snippets.values()).sort((a, b) => a.caption.localeCompare(b.caption));
  }

  public getSnippetCompletionList(): Ace.SnippetCompletion[] {
    return this.snippetCompletionList;
  }

  public hasSnippet(caption: string): boolean {
    return this.snippets.has(caption);
  }

  public addSnippet(caption: string, sql: string): void {
    this.snippets.set(caption, { caption: caption, sql: sql, builtin: false });
    void putUserState("sql-snippet", caption, this.snippets.get(caption)!).catch((error) =>
      console.error("Failed to save SQL snippet:", error)
    );
    this.snippetCompletionList = this.toCompletion();
    this.notifyListeners();
  }

  /**
   * Replace an existing snippet with new names
   */
  public replaceSnippet(old: string, newCaption: string, sql: string): void {
    this.snippets.delete(old);
    void deleteUserState("sql-snippet", old).catch((error) =>
      console.error("Failed to delete old SQL snippet:", error)
    );
    this.addSnippet(newCaption, sql);
  }

  public deleteSnippet(caption: string): void {
    this.snippets.delete(caption);
    void deleteUserState("sql-snippet", caption).catch((error) =>
      console.error("Failed to delete SQL snippet:", error)
    );
    this.snippetCompletionList = this.toCompletion();
    this.notifyListeners();
  }

  private toCompletion(): Ace.SnippetCompletion[] {
    const completions: Ace.SnippetCompletion[] = [];
    this.snippets.forEach((snippet) => {
      completions.push({
        caption: snippet.caption,
        snippet: snippet.sql,
        meta: "snippet",
      });
    });
    return completions.sort((a, b) => {
      return (a.caption as string).localeCompare(b.caption as string);
    });
  }

  // Process connection
  onConnectionChanged(conn: Connection | null): void {
    const useCluster = conn !== null && conn.cluster !== undefined && conn.cluster.length > 0;

    builtinSnippet.forEach((snippet) => {
      this.snippets.set(snippet.caption, {
        sql: useCluster ? snippet.sql.replace("{cluster}", conn!.cluster!) : snippet.sql,
        caption: snippet.caption,
        builtin: true,
      });
    });

    this.snippetCompletionList = this.toCompletion();
    this.notifyListeners();
  }
}
