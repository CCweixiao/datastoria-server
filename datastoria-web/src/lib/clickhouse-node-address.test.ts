import { describe, expect, it } from "vitest";
import { formatClickHouseNodeAddress } from "./clickhouse-node-address";

describe("formatClickHouseNodeAddress", () => {
  it("keeps hostnames and IPv4 addresses in host-port form", () => {
    expect(formatClickHouseNodeAddress("clickhouse-1", 9000)).toBe("clickhouse-1:9000");
    expect(formatClickHouseNodeAddress("10.0.0.12", 9440)).toBe("10.0.0.12:9440");
  });

  it("wraps IPv6 addresses in brackets", () => {
    expect(formatClickHouseNodeAddress("::1", 9000)).toBe("[::1]:9000");
    expect(formatClickHouseNodeAddress("2001:db8::10", 9440)).toBe("[2001:db8::10]:9440");
  });

  it("does not double-wrap bracketed IPv6 addresses", () => {
    expect(formatClickHouseNodeAddress("[::1]", 9000)).toBe("[::1]:9000");
  });
});
