import { backendApiHeaders, backendApiUrl } from "@/lib/backend-api";

/** Returns the Spring Boot origin used by all session APIs. */
export function getSessionApiBase(): string {
  return backendApiUrl("");
}

export function sessionIdentityHeaders(extra?: HeadersInit): HeadersInit {
  return backendApiHeaders(extra);
}
