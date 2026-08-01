import { backendApiFetch, backendApiHeaders, backendApiUrl, readBackendError } from "./backend-api";

export interface AvailableProvider {
  id: string;
  providerKey: string;
  displayName: string;
}

export interface UserModel {
  id: string;
  providerId: string;
  modelKey: string;
  displayName: string;
  description?: string | null;
  enabled: boolean;
  credentialConfigured: boolean;
  maskedHint?: string | null;
  revision: number;
}

export interface UserModelInput {
  providerId: string;
  modelKey: string;
  displayName: string;
  description?: string;
  apiKey?: string;
}

export interface UserProvider {
  id: string;
  providerKey: string;
  displayName: string;
  baseUrl: string;
  authType: string;
  enabled: boolean;
  revision: number;
  credentialConfigured: boolean;
  maskedHint?: string | null;
}

export interface UserProviderInput {
  providerKey: string;
  displayName: string;
  baseUrl: string;
  apiKey?: string;
}

async function checked<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw new Error((await readBackendError(response)).message);
  }
  return (await response.json()) as T;
}

export async function listAvailableProviders(): Promise<AvailableProvider[]> {
  return checked(
    await backendApiFetch(backendApiUrl("/api/ai/providers/available"), {
      headers: backendApiHeaders(),
    })
  );
}

export async function listUserModels(): Promise<UserModel[]> {
  return checked(
    await backendApiFetch(backendApiUrl("/api/me/ai/models"), {
      headers: backendApiHeaders(),
    })
  );
}

export async function listUserProviders(): Promise<UserProvider[]> {
  return checked(
    await backendApiFetch(backendApiUrl("/api/me/ai/providers"), {
      headers: backendApiHeaders(),
    })
  );
}

export async function createUserProvider(input: UserProviderInput): Promise<UserProvider> {
  return checked(
    await backendApiFetch(backendApiUrl("/api/me/ai/providers"), {
      method: "POST",
      headers: backendApiHeaders({ "Content-Type": "application/json" }),
      body: JSON.stringify(input),
    })
  );
}

export async function updateUserProvider(
  provider: UserProvider,
  input: UserProviderInput
): Promise<UserProvider> {
  return checked(
    await backendApiFetch(backendApiUrl(`/api/me/ai/providers/${provider.id}`), {
      method: "PUT",
      headers: backendApiHeaders({
        "Content-Type": "application/json",
        "If-Match": `"${provider.revision}"`,
      }),
      body: JSON.stringify(input),
    })
  );
}

export async function deleteUserProvider(provider: UserProvider): Promise<void> {
  const response = await backendApiFetch(backendApiUrl(`/api/me/ai/providers/${provider.id}`), {
    method: "DELETE",
    headers: backendApiHeaders({ "If-Match": `"${provider.revision}"` }),
  });
  if (!response.ok) {
    throw new Error((await readBackendError(response)).message);
  }
}

export async function createUserModel(input: UserModelInput): Promise<UserModel> {
  return checked(
    await backendApiFetch(backendApiUrl("/api/me/ai/models"), {
      method: "POST",
      headers: backendApiHeaders({ "Content-Type": "application/json" }),
      body: JSON.stringify(input),
    })
  );
}

export async function updateUserModel(model: UserModel, input: UserModelInput): Promise<UserModel> {
  return checked(
    await backendApiFetch(backendApiUrl(`/api/me/ai/models/${model.id}`), {
      method: "PUT",
      headers: backendApiHeaders({
        "Content-Type": "application/json",
        "If-Match": `"${model.revision}"`,
      }),
      body: JSON.stringify(input),
    })
  );
}

export async function deleteUserModel(model: UserModel): Promise<void> {
  const response = await backendApiFetch(backendApiUrl(`/api/me/ai/models/${model.id}`), {
    method: "DELETE",
    headers: backendApiHeaders({ "If-Match": `"${model.revision}"` }),
  });
  if (!response.ok) {
    throw new Error((await readBackendError(response)).message);
  }
}
