import { beforeEach, describe, expect, it, vi } from "vitest";
import { Connection } from "./connection";

const mockGetContext = vi.fn();

vi.mock("@/components/settings/query-context/query-context-manager", () => ({
  QueryContextManager: {
    getInstance: () => ({
      getContext: mockGetContext,
    }),
  },
}));

describe("Connection session ids", () => {
  it("keeps the legacy connection id for non-cluster connections", () => {
    const connection = Connection.create({
      name: "test",
      url: "http://localhost:8123",
      user: "default",
      password: "",
      cluster: "",
      editable: true,
    });

    expect(connection.connectionId).toBe("default@http://localhost:8123");
    expect(connection.legacyConnectionId).toBe("default@http://localhost:8123");
    expect(connection.matchesSessionConnectionId("default@http://localhost:8123")).toBe(true);
  });

  it("includes cluster in the connection id and still matches the legacy id", () => {
    const connection = Connection.create({
      name: "prod",
      url: "https://clickhouse.example.com:8443/path",
      user: "default",
      password: "",
      cluster: "prod_cluster",
      editable: true,
    });

    expect(connection.connectionId).toBe(
      "default@https://clickhouse.example.com:8443?cluster=prod_cluster"
    );
    expect(connection.legacyConnectionId).toBe("default@https://clickhouse.example.com:8443");
    expect(
      connection.matchesSessionConnectionId(
        "default@https://clickhouse.example.com:8443?cluster=prod_cluster"
      )
    ).toBe(true);
    expect(
      connection.matchesSessionConnectionId("default@https://clickhouse.example.com:8443")
    ).toBe(true);
  });

  it("matches stored parameter-format session ids by parsed cluster", () => {
    const connection = Connection.create({
      name: "prod",
      url: "https://clickhouse.example.com:8443/path",
      user: "default",
      password: "",
      cluster: "prod cluster/1",
      editable: true,
    });

    expect(connection.connectionId).toBe(
      "default@https://clickhouse.example.com:8443?cluster=prod%20cluster%2F1"
    );
    expect(
      connection.matchesSessionConnectionId(
        "default@https://clickhouse.example.com:8443?cluster=prod%20cluster%2F1"
      )
    ).toBe(true);
    expect(
      connection.matchesSessionConnectionId(
        "default@https://clickhouse.example.com:8443?cluster=prod%20cluster%2F2"
      )
    ).toBe(false);
  });
});

describe("Connection query context parameters", () => {
  beforeEach(() => {
    mockGetContext.mockReset();
    mockGetContext.mockReturnValue({
      max_execution_time: 60,
      output_format_pretty_row_numbers: true,
      default_format: "JSONCompactEachRow",
    });
    vi.restoreAllMocks();
  });

  it("adds query context key-values as query parameters for query()", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response('{"data":[]}', { status: 200 }));
    const connection = Connection.create({
      id: "connection-1",
      name: "test",
      url: "http://localhost:8123",
      user: "default",
      password: "",
      cluster: "",
      editable: true,
    });

    await connection.query("SELECT 1").response;

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][0]).toBe(
      "http://127.0.0.1:8080/api/connections/connection-1/query"
    );
    const body = JSON.parse(String(fetchMock.mock.calls[0][1]?.body));
    expect(body.parameters.max_execution_time).toBe(60);
    expect(body.parameters.output_format_pretty_row_numbers).toBe(true);
    expect(body.parameters.default_format).toBe("JSONCompactEachRow");
  });

  it("keeps request params as highest precedence over query context", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response('{"data":[]}', { status: 200 }));
    const connection = Connection.create({
      id: "connection-2",
      name: "test",
      url: "http://localhost:8123?max_execution_time=5",
      user: "default",
      password: "",
      cluster: "",
      editable: true,
    });

    await connection.query("SELECT 1", {
      max_execution_time: 10,
      default_format: "JSON",
    }).response;

    const body = JSON.parse(String(fetchMock.mock.calls[0][1]?.body));
    expect(body.parameters.max_execution_time).toBe(10);
    expect(body.parameters.default_format).toBe("JSON");
  });

  it("adds query context key-values for queryRawResponse()", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response("stream", { status: 200 }));
    const connection = Connection.create({
      id: "connection-3",
      name: "test",
      url: "http://localhost:8123",
      user: "default",
      password: "",
      cluster: "",
      editable: true,
    });

    await connection.queryRawResponse("SELECT 1").response;

    const body = JSON.parse(String(fetchMock.mock.calls[0][1]?.body));
    expect(body.parameters.max_execution_time).toBe(60);
    expect(body.parameters.output_format_pretty_row_numbers).toBe(true);
  });

  it("delegates selected-node queries to Spring without exposing the password", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response('{"data":[]}', { status: 200 }));
    const connection = Connection.create({
      id: "connection-cluster",
      name: "cluster",
      url: "http://localhost:8123",
      user: "external",
      password: "must-not-leave-the-browser",
      cluster: "prod",
      editable: true,
    });
    connection.metadata.remoteHostName = "node-1.example";
    connection.metadata.internalUser = "internal";

    await connection.queryOnNode("SELECT 1").response;

    const body = JSON.parse(String(fetchMock.mock.calls[0][1]?.body));
    expect(body.query).toBe("SELECT 1");
    expect(body.targetNode).toBe("node-1.example");
    expect(body.targetUser).toBe("internal");
    expect(JSON.stringify(body)).not.toContain("must-not-leave-the-browser");
  });

  it("uses Spring ProblemDetail detail as the query error message", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({
          title: "ClickHouse query failed",
          detail: "The current user cannot read system.processes.",
          status: 403,
        }),
        { status: 403, headers: { "Content-Type": "application/problem+json" } }
      )
    );
    const connection = Connection.create({
      id: "connection-4",
      name: "test",
      url: "http://localhost:8123",
      user: "default",
      password: "",
      cluster: "",
      editable: true,
    });

    await expect(connection.query("SELECT * FROM system.processes").response).rejects.toMatchObject(
      {
        message: "The current user cannot read system.processes.",
        httpStatus: 403,
      }
    );
  });
});
