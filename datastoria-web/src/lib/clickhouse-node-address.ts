export function formatClickHouseNodeAddress(host: string, port: number): string {
  const normalizedHost = host.trim();
  if (normalizedHost.startsWith("[") && normalizedHost.endsWith("]")) {
    return `${normalizedHost}:${port}`;
  }
  return normalizedHost.includes(":") ? `[${normalizedHost}]:${port}` : `${normalizedHost}:${port}`;
}
