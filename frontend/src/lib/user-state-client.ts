import { backendApiHeaders, backendApiUrl } from "@/lib/backend-api";

export type UserStateEntry<T> = {
  key: string;
  value: T;
  revision: number;
};

export async function listUserState<T>(namespace: string): Promise<UserStateEntry<T>[]> {
  const response = await fetch(backendApiUrl(`/api/me/state/${encodeURIComponent(namespace)}`), {
    headers: backendApiHeaders(),
  });
  if (!response.ok) {
    throw new Error(`Failed to load ${namespace} state: ${response.status}`);
  }
  return (await response.json()) as UserStateEntry<T>[];
}

export async function putUserState<T>(
  namespace: string,
  key: string,
  value: T
): Promise<UserStateEntry<T>> {
  const response = await fetch(
    backendApiUrl(`/api/me/state/${encodeURIComponent(namespace)}/${encodeURIComponent(key)}`),
    {
      method: "PUT",
      headers: backendApiHeaders({ "Content-Type": "application/json" }),
      body: JSON.stringify({ value }),
    }
  );
  if (!response.ok) {
    throw new Error(`Failed to save ${namespace}/${key}: ${response.status}`);
  }
  return (await response.json()) as UserStateEntry<T>;
}

export async function deleteUserState(namespace: string, key: string): Promise<void> {
  const response = await fetch(
    backendApiUrl(`/api/me/state/${encodeURIComponent(namespace)}/${encodeURIComponent(key)}`),
    { method: "DELETE", headers: backendApiHeaders() }
  );
  if (!response.ok && response.status !== 404) {
    throw new Error(`Failed to delete ${namespace}/${key}: ${response.status}`);
  }
}
