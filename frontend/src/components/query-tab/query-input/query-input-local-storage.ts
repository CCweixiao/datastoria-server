import { listUserState, putUserState } from "@/lib/user-state-client";

export class QueryInputLocalStorage {
  private static inputs = new Map<string, string>();
  private static hydration = listUserState<string>("query-draft")
    .then((entries) => entries.forEach((entry) => this.inputs.set(entry.key, entry.value)))
    .catch((error) => console.error("Failed to load query drafts:", error));

  public static getInput(key: string): string {
    return this.inputs.get(key) ?? "";
  }

  public static async getInputAsync(key: string): Promise<string> {
    await this.hydration;
    return this.getInput(key);
  }

  public static saveInput(text: string, key: string): void {
    this.inputs.set(key, text);
    void putUserState("query-draft", key, text).catch((error) =>
      console.error("Failed to save query draft:", error)
    );
  }
}
