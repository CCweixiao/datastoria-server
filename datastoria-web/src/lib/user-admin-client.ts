import { backendApiFetch, backendApiHeaders, backendApiUrl, readBackendError } from "./backend-api";

export type ManagedUser = {
  userId: string;
  username: string;
  email?: string | null;
  role: "USER";
  tenantId: string;
  status: number;
  createdAt: string;
};

export type CreateManagedUser = {
  username: string;
  email?: string;
  password: string;
};

export type UpdateManagedUser = {
  email?: string;
  status: 0 | 1;
};

async function requireSuccess(response: Response): Promise<Response> {
  if (!response.ok) {
    throw new Error((await readBackendError(response)).message);
  }
  return response;
}

export async function listManagedUsers(): Promise<ManagedUser[]> {
  const response = await requireSuccess(
    await backendApiFetch(backendApiUrl("/api/admin/users"), {
      headers: backendApiHeaders(),
    })
  );
  return response.json() as Promise<ManagedUser[]>;
}

export async function createManagedUser(input: CreateManagedUser): Promise<ManagedUser> {
  const response = await requireSuccess(
    await backendApiFetch(backendApiUrl("/api/admin/users"), {
      method: "POST",
      headers: backendApiHeaders({ "Content-Type": "application/json" }),
      body: JSON.stringify({ ...input, role: "USER" }),
    })
  );
  return response.json() as Promise<ManagedUser>;
}

export async function updateManagedUser(
  userId: string,
  input: UpdateManagedUser
): Promise<ManagedUser> {
  const response = await requireSuccess(
    await backendApiFetch(backendApiUrl(`/api/admin/users/${userId}`), {
      method: "PUT",
      headers: backendApiHeaders({ "Content-Type": "application/json" }),
      body: JSON.stringify({ ...input, role: "USER", status: String(input.status) }),
    })
  );
  return response.json() as Promise<ManagedUser>;
}

export async function resetManagedUserPassword(userId: string, password: string): Promise<void> {
  await requireSuccess(
    await backendApiFetch(backendApiUrl(`/api/admin/users/${userId}/reset-password`), {
      method: "POST",
      headers: backendApiHeaders({ "Content-Type": "application/json" }),
      body: JSON.stringify({ password }),
    })
  );
}

export async function deleteManagedUser(userId: string): Promise<void> {
  await requireSuccess(
    await backendApiFetch(backendApiUrl(`/api/admin/users/${userId}`), {
      method: "DELETE",
      headers: backendApiHeaders(),
    })
  );
}
