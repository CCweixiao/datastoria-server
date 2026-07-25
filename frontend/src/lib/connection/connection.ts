import type { DependencyTableInfo } from "@/components/dependency-view/dependency-types";
import { QueryContextManager } from "@/components/settings/query-context/query-context-manager";
import { backendApiFetch, readBackendError } from "@/lib/backend-api";
import type { ClickHouseSetting } from "@/lib/clickhouse/clickhouse-setting-loader";
import type { ConnectionConfig } from "./connection-config";

// Re-export ConnectionConfig for convenience
export type { ConnectionConfig };

type QueryHeaders = Record<string, string>;
type ErrorWithCaptureStackTrace = ErrorConstructor & {
  captureStackTrace?: (
    targetObject: object,
    constructorOpt?: (...args: never[]) => unknown
  ) => void;
};

type ParsedSessionConnectionId = {
  user: string;
  host: string;
  cluster: string | null;
};

function parseSessionConnectionId(connectionId: string): ParsedSessionConnectionId | null {
  const separatorIndex = connectionId.indexOf("@");
  if (separatorIndex <= 0) {
    return null;
  }

  const user = connectionId.slice(0, separatorIndex);
  const hostWithParameters = connectionId.slice(separatorIndex + 1);
  const parameterIndex = hostWithParameters.indexOf("?");
  if (parameterIndex < 0) {
    return {
      user,
      host: hostWithParameters,
      cluster: null,
    };
  }

  const host = hostWithParameters.slice(0, parameterIndex);
  const parameters = new URLSearchParams(hostWithParameters.slice(parameterIndex + 1));
  return {
    user,
    host,
    cluster: parameters.get("cluster"),
  };
}

export class QueryError extends Error {
  httpStatus?: number;
  httpHeaders?: QueryHeaders;
  data: unknown;
  errorCode?: string;

  constructor(message: string, httpStatus?: number, httpHeaders?: QueryHeaders, data?: unknown) {
    super(message);
    this.name = "QueryError";
    this.httpStatus = httpStatus;
    this.httpHeaders = httpHeaders;
    this.data = data;
    this.errorCode = httpHeaders ? httpHeaders["x-clickhouse-exception-code"]?.trim() : undefined;

    // Explicitly set prototype to ensure instanceof works correctly across async boundaries
    Object.setPrototypeOf(this, QueryError.prototype);

    // Maintains proper stack trace for where our error was thrown (only available on V8)
    const errorConstructor = Error as ErrorWithCaptureStackTrace;
    if (typeof errorConstructor.captureStackTrace === "function") {
      errorConstructor.captureStackTrace(this, QueryError);
    }
  }
}

export interface QueryResponseData {
  text: () => string;
  json: <T = unknown>() => T;
}

export interface QueryResponse {
  httpStatus: number;
  httpHeaders: QueryHeaders;
  data: QueryResponseData;
}

export interface TableInfo {
  database: string;
  table: string;
  comment?: string | null;
  columns?: Array<{ name: string; type: string }> | string[];
  engine?: string | null;
}

export interface DatabaseInfo {
  name: string;
  engine: string;
  comment?: string | null;
}

export interface JSONCompactFormatResponse {
  data: unknown[][];
  meta: { name: string; type: string }[];
  rows: number;
  statistics: {
    elapsed: number;
    rows_read: number;
    bytes_read: number;
  };
}

export interface JSONFormatResponse {
  data: Record<string, unknown>[];
  meta: { name: string; type: string }[];
  rows: number;
  statistics: {
    elapsed: number;
    rows_read: number;
    bytes_read: number;
  };
}

export interface ConnectionMetadata {
  // The display name of the connection, will be used to display in the UI
  displayName: string;

  // The node that initial query is performed on
  // It will be used to execute future queries in users intend to perform query on that node
  // ONLY available under the cluster mode
  remoteHostName?: string;
  serverVersion?: string;

  // The current user at server side, will be used to execute queries that require the internal user name instead of the client side configured user name
  internalUser: string;

  // Server timezone
  timezone: string;

  //
  // Capabilities
  //
  // Table columns
  function_table_has_description_column: boolean;
  metric_log_table_has_ProfileEvent_MergeSourceParts: boolean;
  metric_log_table_has_ProfileEvent_MutationTotalParts: boolean;
  query_log_table_has_hostname_column: boolean;
  span_log_table_has_hostname_column: boolean;
  part_log_table_has_node_name_column: boolean;

  // Functions
  has_format_query_function: boolean;

  // Settings
  is_readonly_skip_unavailable_shards: boolean;

  tableNames?: Map<string, TableInfo>;
  databaseNames?: Map<string, DatabaseInfo>;

  // hostName() from all nodes
  hostNames?: Set<string>;

  // Cached dependency data - loaded on demand and cached here
  dependencyData?: {
    tables: Map<string, DependencyTableInfo>;
    innerTables: Map<string, DependencyTableInfo>;
  };

  // Cached ProfileEvents from system.events - used for SQL validation
  // If it fails to get events, validation will be skipped
  profileEvents?: Set<string>;

  // Cached ClickHouse settings metadata used by chat suggestions and markdown hovers
  clickhouseSettings: Map<string, ClickHouseSetting>;
}

const USER_CANCELLED_ERROR_MESSAGE = "User cancelled";

export class Connection {
  // Static config
  readonly id?: string;
  readonly name: string;
  readonly url: string;
  readonly user: string;
  readonly cluster?: string;

  // Runtime properties
  readonly host: string;
  readonly path: string;
  readonly userParams: Record<string, unknown>;

  // Connection metadata information
  metadata: ConnectionMetadata;

  readonly connectionId: string;
  readonly legacyConnectionId: string;

  private constructor(config: ConnectionConfig) {
    this.id = config.id;
    this.name = config.name;
    this.url = config.url;
    this.user = config.user;
    this.cluster = config.cluster;

    const urlObj = new URL(config.url);
    this.host = urlObj.origin;
    this.path = urlObj.pathname === "" ? "/" : urlObj.pathname;

    this.userParams = {};
    urlObj.searchParams.forEach((val, key) => {
      this.userParams[key] = val;
    });

    if (this.userParams["max_execution_time"] !== undefined) {
      const maxExecTime = this.userParams["max_execution_time"];
      if (typeof maxExecTime === "string") {
        this.userParams["max_execution_time"] = parseInt(maxExecTime, 10);
      }
    }

    this.legacyConnectionId = `${config.user}@${this.host}`;
    this.connectionId =
      config.id ??
      (config.cluster && config.cluster.length > 0
        ? `${config.user}@${this.host}?cluster=${encodeURIComponent(config.cluster)}`
        : this.legacyConnectionId);

    // Initialize metadata with defaults
    this.metadata = {
      displayName: config.name,

      internalUser: config.user, // Default to external configured user
      timezone: "UTC", // Default timezone

      // Tables
      function_table_has_description_column: false,
      metric_log_table_has_ProfileEvent_MergeSourceParts: false,
      metric_log_table_has_ProfileEvent_MutationTotalParts: false,
      query_log_table_has_hostname_column: false,
      span_log_table_has_hostname_column: false,
      part_log_table_has_node_name_column: false,

      // Functions
      has_format_query_function: false,

      // Settings, Assume it's readonly by default in case we can't access the settings
      is_readonly_skip_unavailable_shards: true,
      clickhouseSettings: new Map<string, ClickHouseSetting>(),
    };
  }

  static create(config: ConnectionConfig): Connection {
    return new Connection(config);
  }

  matchesSessionConnectionId(connectionId?: string | null): boolean {
    if (!connectionId) {
      return false;
    }
    if (connectionId === this.connectionId || connectionId === this.legacyConnectionId) {
      return true;
    }

    const parsedConnectionId = parseSessionConnectionId(connectionId);
    if (!parsedConnectionId) {
      return false;
    }

    const cluster = this.cluster && this.cluster.length > 0 ? this.cluster : null;
    return (
      parsedConnectionId.user === this.user &&
      parsedConnectionId.host === this.host &&
      parsedConnectionId.cluster === cluster
    );
  }

  private buildQueryParameters(userParams?: Record<string, unknown>): Record<string, unknown> {
    // Precedence: URL params < query context < request params
    const queryParameters: Record<string, unknown> = Object.assign({}, this.userParams);
    Object.assign(queryParameters, QueryContextManager.getInstance().getContext());
    if (userParams) {
      Object.assign(queryParameters, userParams);
    }
    if (!queryParameters["default_format"]) {
      queryParameters["default_format"] = "JSONCompact";
    }
    return queryParameters;
  }

  public query(
    sql: string,
    params?: Record<string, unknown>,
    _headers?: Record<string, string>
  ): { response: Promise<QueryResponse>; abortController: AbortController } {
    // Validate connection is properly initialized
    if (!this.host || !this.path) {
      throw new QueryError(
        `Connection not properly initialized. Host: ${this.host}, Path: ${this.path}`
      );
    }

    // Apply cluster template replacements
    const [replacedSql] = this.resolveClusterTemplates(sql);
    sql = replacedSql;

    const queryParameters = this.buildQueryParameters(params);

    // Can't add this header automatically
    // Some clusters are deployed after load balancers which may have enable CORS already
    // queryParameters["add_http_cors_header"] = "1";

    // Create abort controller for the caller to use
    const abortController = new AbortController();

    const response = (async (): Promise<QueryResponse> => {
      try {
        if (!this.id) {
          throw new QueryError("Connection must be saved before executing a query");
        }
        const apiBase = (
          process.env.NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL ?? "http://127.0.0.1:8080"
        ).replace(/\/+$/, "");
        const identity = process.env.NEXT_PUBLIC_DATASTORIA_DEV_USER_EMAIL;
        const response = await backendApiFetch(`${apiBase}/api/connections/${this.id}/query`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            ...(identity ? { "x-datastoria-user-email": identity } : {}),
          },
          body: JSON.stringify({ query: sql, parameters: queryParameters }),
          signal: abortController.signal,
        });

        if (!response.ok) {
          const { message, body } = await readBackendError(
            response,
            `Failed to execute query, got HTTP status ${response.status} ${response.statusText} from server`
          );
          const clickHouseErrorCode = response.headers.get("x-clickhouse-exception-code");
          throw new QueryError(
            clickHouseErrorCode
              ? `Failed to execute query, got ClickHouse Exception Code: ${clickHouseErrorCode}`
              : message,
            response.status,
            Object.fromEntries(response.headers.entries()),
            body
          );
        }

        // Read successful response body as text first (can only be read once).
        const responseText = await response.text();
        const data: QueryResponseData = {
          text: () => responseText,
          json: <T = unknown>() => JSON.parse(responseText) as T,
        };

        return {
          httpStatus: response.status,
          httpHeaders: Object.fromEntries(response.headers.entries()),
          data: data,
        };
      } catch (error: unknown) {
        // If it's already an QueryError, re-throw it
        if (error instanceof QueryError) {
          throw error;
        }

        // Handle abort errors (can be Error with name "AbortError" or DOMException)
        if (
          (error instanceof Error && error.name === "AbortError") ||
          (error instanceof DOMException && error.name === "AbortError")
        ) {
          throw new QueryError("Request was cancelled by user");
        }

        if (error === USER_CANCELLED_ERROR_MESSAGE) {
          throw new QueryError(error as string);
        }

        // Re-throw as QueryError-like error
        const errorMessage = error instanceof Error ? error.message : "Unknown error";
        if (errorMessage === "Failed to fetch") {
          const errorDetails =
            `Failed to connect to ${this.host}${this.path} \n` +
            `Possible causes: CORS issue, network error, DNS problem, or invalid server URL. ` +
            `Please check the connection configuration and ensure the server allows requests from this origin.`;
          throw new QueryError(errorDetails);
        }
        throw new QueryError(errorMessage);
      }
    })();

    return { response, abortController };
  }

  /**
   * Execute a query and return the raw fetch Response for streaming.
   * The caller is responsible for reading the response body (e.g. via response.body.getReader()).
   * Does not consume the response body.
   */
  public queryRawResponse(
    sql: string,
    params?: Record<string, unknown>,
    _headers?: Record<string, string>
  ): { response: Promise<Response>; abortController: AbortController } {
    if (!this.host || !this.path) {
      throw new QueryError(
        `Connection not properly initialized. Host: ${this.host}, Path: ${this.path}`
      );
    }

    const [replacedSql] = this.resolveClusterTemplates(sql);
    sql = replacedSql;

    const queryParameters = this.buildQueryParameters(params);

    const abortController = new AbortController();

    const response = (async (): Promise<Response> => {
      if (!this.id) {
        throw new QueryError("Connection must be saved before executing a query");
      }
      const apiBase = (
        process.env.NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL ?? "http://127.0.0.1:8080"
      ).replace(/\/+$/, "");
      const identity = process.env.NEXT_PUBLIC_DATASTORIA_DEV_USER_EMAIL;
      const res = await backendApiFetch(`${apiBase}/api/connections/${this.id}/query`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...(identity ? { "x-datastoria-user-email": identity } : {}),
        },
        body: JSON.stringify({ query: sql, parameters: queryParameters }),
        signal: abortController.signal,
      });

      if (!res.ok) {
        const { message, body } = await readBackendError(
          res,
          `Failed to execute query, got HTTP status ${res.status} ${res.statusText} from server`
        );
        const clickHouseErrorCode = res.headers.get("x-clickhouse-exception-code");
        throw new QueryError(
          clickHouseErrorCode
            ? `Failed to execute query, got ClickHouse Exception Code: ${clickHouseErrorCode}`
            : message,
          res.status,
          Object.fromEntries(res.headers.entries()),
          body
        );
      }

      return res;
    })();

    return { response, abortController };
  }

  /**
   * Execute a query with JSONCompact format and return parsed response.
   */
  public async queryJsonCompact(sql: string): Promise<JSONCompactFormatResponse> {
    const { response } = this.query(sql, { default_format: "JSONCompact" });
    const apiResponse = await response;
    return apiResponse.data.json<JSONCompactFormatResponse>();
  }

  public queryOnNode(
    sql: string,
    params?: Record<string, unknown>,
    headers?: Record<string, string>
  ): { response: Promise<QueryResponse>; abortController: AbortController } {
    const node = this.metadata.remoteHostName;

    if (node === undefined) {
      // Fallback to query on any node
      return this.query(sql, params, headers);
    }

    // Apply cluster template replacements
    const [processedSql, hasClusterFunctions] = this.resolveClusterTemplates(sql);
    if (hasClusterFunctions) {
      if (!this.metadata.is_readonly_skip_unavailable_shards) {
        params = {
          ...params,

          // For cluster query, skip unavailable shard by default
          skip_unavailable_shards: 1,
        };
      }

      // Since cluster/clusterAllReplica is used, don't use remote function to execute this sql
      return this.query(processedSql, params, headers);
    }

    return this.queryWithTarget(processedSql, params, headers, node, this.metadata.internalUser);
  }

  private queryWithTarget(
    sql: string,
    params: Record<string, unknown> | undefined,
    _headers: Record<string, string> | undefined,
    targetNode: string,
    targetUser: string
  ): { response: Promise<QueryResponse>; abortController: AbortController } {
    const queryParameters = this.buildQueryParameters(params);
    const abortController = new AbortController();
    const response = (async (): Promise<QueryResponse> => {
      if (!this.id) {
        throw new QueryError("Connection must be saved before executing a query");
      }
      const apiBase = (
        process.env.NEXT_PUBLIC_DATASTORIA_JAVA_API_BASE_URL ?? "http://127.0.0.1:8080"
      ).replace(/\/+$/, "");
      const identity = process.env.NEXT_PUBLIC_DATASTORIA_DEV_USER_EMAIL;
      const res = await backendApiFetch(`${apiBase}/api/connections/${this.id}/query`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...(identity ? { "x-datastoria-user-email": identity } : {}),
        },
        body: JSON.stringify({ query: sql, parameters: queryParameters, targetNode, targetUser }),
        signal: abortController.signal,
      });
      if (!res.ok) {
        const { message, body } = await readBackendError(
          res,
          `Failed to execute query, got HTTP status ${res.status} ${res.statusText} from server`
        );
        const clickHouseErrorCode = res.headers.get("x-clickhouse-exception-code");
        throw new QueryError(
          clickHouseErrorCode
            ? `Failed to execute query, got ClickHouse Exception Code: ${clickHouseErrorCode}`
            : message,
          res.status,
          Object.fromEntries(res.headers.entries()),
          body
        );
      }
      const responseText = await res.text();
      return {
        httpStatus: res.status,
        httpHeaders: Object.fromEntries(res.headers.entries()),
        data: {
          text: () => responseText,
          json: <T = unknown>() => JSON.parse(responseText) as T,
        },
      };
    })();
    return { response, abortController };
  }

  /**
   * Process cluster template variables in SQL query.
   * Templates:
   * - {clusterAllReplicas:table} -> clusterAllReplicas('{cluster}', table) or table
   * - {cluster:table} -> cluster('{cluster}', table) or table
   * - {table:table} -> table
   * - {cluster} -> actual cluster name (simple variable, no colon)
   *
   * @returns [processedSql, hasClusterFunctions] - The processed SQL and whether cluster functions were added
   */
  private resolveClusterTemplates(sql: string): [string, boolean] {
    const hasCluster = this.cluster && this.cluster.length > 0;
    let usedClusterFunctions = false;

    // Replace {clusterAllReplicas:table_name} patterns
    sql = sql.replace(/\{clusterAllReplicas:([^}]+)\}/g, (_match, tableName) => {
      if (hasCluster) {
        usedClusterFunctions = true;
        return `clusterAllReplicas('{cluster}', ${tableName})`;
      }
      return tableName;
    });

    // Replace {cluster:table_name} patterns (note: different from simple {cluster})
    sql = sql.replace(/\{cluster:([^}]+)\}/g, (_match, tableName) => {
      if (hasCluster) {
        usedClusterFunctions = true;
        return `cluster('{cluster}', ${tableName})`;
      }
      return tableName;
    });

    // Replace {table:table_name} patterns (no cluster wrapping)
    sql = sql.replace(/\{table:([^}]+)\}/g, (_match, tableName) => {
      return tableName;
    });

    // Replace {cluster} with actual cluster name (simple variable without colon)
    if (hasCluster) {
      sql = sql.replace(/\{cluster\}/g, this.cluster);
    }

    return [sql, usedClusterFunctions];
  }
}
