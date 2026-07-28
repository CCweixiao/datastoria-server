package io.github.ccweixiao.datastoria.agent.runtime;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import reactor.core.publisher.Mono;

/**
 * Model-backed SQL workflow wrappers.
 *
 * <p>The nested model receives no tools and must return JSON. All SQL is then independently checked
 * by the server-side read-only classifier and real ClickHouse validation tool before it crosses the
 * tool boundary.
 */
public final class SqlWorkflowAgentTools {

  private static final Set<String> CHART_TYPES =
      Set.of("line", "bar", "area", "pie", "table", "none");
  private final Model model;
  private final ClickHouseAgentTools clickHouseTools;
  private final ObjectMapper mapper;
  private final AgentToolExecutionPolicy executionPolicy;
  private final ClickHouseReadOnlySqlClassifier classifier = new ClickHouseReadOnlySqlClassifier();

  public SqlWorkflowAgentTools(
      Model model, ClickHouseAgentTools clickHouseTools, ObjectMapper mapper) {
    this(model, clickHouseTools, mapper, AgentToolExecutionPolicy.untracked());
  }

  public SqlWorkflowAgentTools(
      Model model,
      ClickHouseAgentTools clickHouseTools,
      ObjectMapper mapper,
      AgentToolExecutionPolicy executionPolicy) {
    this.model = model;
    this.clickHouseTools = clickHouseTools;
    this.mapper = mapper == null ? new ObjectMapper() : mapper;
    this.executionPolicy = executionPolicy;
  }

  @Tool(
      name = "generate_sql",
      description =
          "Generate one read-only ClickHouse SQL query from a user question and explicit schema "
              + "hints, then validate it against the run's real connection.",
      readOnly = true)
  public Mono<String> generateSql(
      @ToolParam(name = "userQuestion", required = true, description = "User data request")
          String userQuestion,
      @ToolParam(
              name = "previousValidationError",
              required = false,
              description = "Exact validation error from a prior attempt")
          String previousValidationError,
      @ToolParam(name = "schemaHints", required = false, description = "Explicit table schemas")
          List<SchemaHint> schemaHints,
      @ToolParam(name = "context", required = false, description = "Database and user context")
          SqlContext context) {
    requireRuntime();
    if (userQuestion == null || userQuestion.isBlank()) {
      return Mono.error(new IllegalArgumentException("userQuestion is required"));
    }
    String prompt =
        """
        Generate one read-only ClickHouse query. Return JSON only:
        {"sql":"...","notes":"...","assumptions":[],"needs_clarification":false,"questions":[]}
        Rules: use only the supplied schema, fully qualify tables, use a bounded time range when
        relevant, include LIMIT for result queries, and do not append a semicolon.
        """
            + "\nQuestion:\n"
            + userQuestion
            + "\nPrevious validation error:\n"
            + safe(previousValidationError)
            + "\nContext:\n"
            + json(context)
            + "\nSchema hints:\n"
            + json(schemaHints == null ? List.of() : schemaHints);
    return executionPolicy.guard(
        "generate_sql",
        nestedJson("ClickHouse SQL generation wrapper", prompt)
            .flatMap(this::validateGeneratedSql)
            .map(JsonNode::toString));
  }

  @Tool(
      name = "optimize_sql",
      description =
          "Rewrite one read-only ClickHouse SQL query using supplied evidence and validate the "
              + "rewrite against the real connection.",
      readOnly = true)
  public Mono<String> optimizeSql(
      @ToolParam(name = "sql", required = true, description = "Original read-only SQL") String sql,
      @ToolParam(name = "goal", required = false, description = "latency, memory, bytes, or other")
          String goal,
      @ToolParam(
              name = "evidence",
              required = true,
              description = "Evidence from collect_sql_optimization_evidence")
          Map<String, Object> evidence) {
    requireRuntime();
    String original = classifier.requireReadOnly(sql);
    String resolvedGoal = goal == null || goal.isBlank() ? "other" : goal;
    if (!Set.of("latency", "memory", "bytes", "dashboard", "other").contains(resolvedGoal)) {
      return Mono.error(new IllegalArgumentException("Unsupported optimization goal"));
    }
    if (evidence == null || evidence.isEmpty()) {
      return Mono.error(new IllegalArgumentException("evidence is required"));
    }
    String prompt =
        """
        Rewrite the ClickHouse SQL using only the supplied evidence. Return JSON only:
        {"optimized_sql":"...","changes":["..."],"assumptions":[]}
        Preserve semantics, fully qualify tables, keep the query read-only, and do not append a
        semicolon. If evidence does not justify a rewrite, return the original SQL unchanged.
        """
            + "\nGoal: "
            + resolvedGoal
            + "\nOriginal SQL:\n"
            + original
            + "\nEvidence:\n"
            + json(evidence);
    return executionPolicy.guard(
        "optimize_sql",
        nestedJson("ClickHouse evidence-driven SQL optimization wrapper", prompt)
            .flatMap(node -> validateOptimizedSql(original, resolvedGoal, node))
            .map(JsonNode::toString));
  }

  @Tool(
      name = "generate_visualization",
      description =
          "Return a declarative visualization descriptor for validated SQL. Never returns "
              + "executable browser code or markup.",
      readOnly = true)
  public Mono<String> generateVisualization(
      @ToolParam(name = "userQuestion", required = true, description = "Original user question")
          String userQuestion,
      @ToolParam(name = "sql", required = true, description = "Validated read-only SQL")
          String sql) {
    requireRuntime();
    String safeSql = classifier.requireReadOnly(sql);
    String prompt =
        """
        Choose a visualization for this ClickHouse query. Return JSON only:
        {"type":"line|bar|area|pie|table|none","title":"...","title_align":"left|center|right",
        "width":1,"legend_placement":"none|bottom|right|inside","legend_values":[],
        "value_format":null}
        Honor an explicit chart request. Prefer line for time trends, bar for categorical
        comparisons, pie only for a small distribution, and table for raw rows.
        """
            + "\nQuestion:\n"
            + safe(userQuestion)
            + "\nSQL:\n"
            + safeSql;
    Mono<String> operation =
        clickHouseTools
            .validateSql(safeSql)
            .map(this::parseJson)
            .flatMap(
                validation -> {
                  if (!validation.path("success").asBoolean()) {
                    return Mono.error(
                        new IllegalArgumentException(
                            "Visualization SQL failed validation: "
                                + validation.path("error").asText()));
                  }
                  return nestedJson("Declarative data visualization planner", prompt);
                })
            .map(node -> visualizationJson(node, safeSql))
            .map(JsonNode::toString);
    return executionPolicy.guard("generate_visualization", operation);
  }

  private Mono<JsonNode> validateGeneratedSql(JsonNode generated) {
    if (generated.path("needs_clarification").asBoolean(false)) {
      ObjectNode result = mapper.createObjectNode();
      result.put("sql", "");
      result.put("notes", generated.path("notes").asText("More schema context is required."));
      result.set("assumptions", stringArray(generated.path("assumptions")));
      result.put("needs_clarification", true);
      result.set("questions", stringArray(generated.path("questions")));
      return Mono.just(result);
    }
    String sql = classifier.requireReadOnly(generated.path("sql").asText());
    return clickHouseTools
        .validateSql(sql)
        .map(this::parseJson)
        .map(
            validation -> {
              ObjectNode result = mapper.createObjectNode();
              result.put("sql", sql);
              result.put("notes", generated.path("notes").asText(""));
              result.set("assumptions", stringArray(generated.path("assumptions")));
              result.put("needs_clarification", !validation.path("success").asBoolean());
              ArrayNode questions = result.putArray("questions");
              if (!validation.path("success").asBoolean()) {
                questions.add(
                    "The generated SQL failed validation: " + validation.path("error").asText());
              }
              result.set("validation", validation);
              return result;
            });
  }

  private Mono<JsonNode> validateOptimizedSql(String original, String goal, JsonNode generated) {
    String optimized = classifier.requireReadOnly(generated.path("optimized_sql").asText());
    return clickHouseTools
        .validateSql(optimized)
        .map(this::parseJson)
        .map(
            validation -> {
              ObjectNode result = mapper.createObjectNode();
              result.put("original_sql", original);
              result.put("optimized_sql", optimized);
              result.put("goal", goal);
              result.set("changes", stringArray(generated.path("changes")));
              result.set("assumptions", stringArray(generated.path("assumptions")));
              result.set("validation", validation);
              return result;
            });
  }

  private ObjectNode visualizationJson(JsonNode generated, String sql) {
    String type = generated.path("type").asText("table");
    if (!CHART_TYPES.contains(type)) {
      type = "table";
    }
    ObjectNode result = mapper.createObjectNode();
    result.put("type", type);
    String title = generated.path("title").asText();
    if (!title.isBlank()) {
      ObjectNode titleOption = result.putObject("titleOption");
      titleOption.put("title", title.substring(0, Math.min(title.length(), 200)));
      String align = generated.path("title_align").asText("left");
      titleOption.put("align", Set.of("left", "center", "right").contains(align) ? align : "left");
    }
    int width = Math.max(1, Math.min(generated.path("width").asInt(12), 12));
    result.put("width", width);
    String placement = generated.path("legend_placement").asText("none");
    if (!Set.of("none", "bottom", "right", "inside").contains(placement)) {
      placement = "none";
    }
    ObjectNode legend = result.putObject("legendOption");
    legend.put("placement", placement);
    ArrayNode values = legend.putArray("values");
    for (JsonNode value : generated.path("legend_values")) {
      if (Set.of("min", "max", "sum", "avg", "count").contains(value.asText())) {
        values.add(value.asText());
      }
    }
    String valueFormat = generated.path("value_format").asText();
    if (Set.of(
            "short_number",
            "comma_number",
            "binary_size",
            "percentage",
            "millisecond",
            "microsecond")
        .contains(valueFormat)) {
      result.put("valueFormat", valueFormat);
    }
    result.putObject("datasource").put("sql", sql);
    return result;
  }

  private Mono<JsonNode> nestedJson(String systemPrompt, String userPrompt) {
    List<Msg> messages =
        List.of(
            Msg.builder().role(MsgRole.SYSTEM).textContent(systemPrompt).build(),
            Msg.builder().role(MsgRole.USER).textContent(userPrompt).build());
    GenerateOptions options =
        GenerateOptions.builder().stream(true).temperature(0.0).maxTokens(2_000).build();
    return model.stream(messages, List.of(), options)
        .flatMapIterable(response -> response.getContent())
        .filter(TextBlock.class::isInstance)
        .cast(TextBlock.class)
        .map(TextBlock::getText)
        .collectList()
        .map(parts -> parseJson(String.join("", parts)));
  }

  private JsonNode parseJson(String value) {
    String json = value == null ? "" : value.trim();
    if (json.startsWith("```")) {
      int firstNewline = json.indexOf('\n');
      int closing = json.lastIndexOf("```");
      if (firstNewline >= 0 && closing > firstNewline) {
        json = json.substring(firstNewline + 1, closing).trim();
      }
    }
    try {
      JsonNode parsed = mapper.readTree(json);
      if (!parsed.isObject()) {
        throw new IllegalArgumentException("Nested model must return a JSON object");
      }
      return parsed;
    } catch (JsonProcessingException error) {
      throw new IllegalArgumentException("Nested model returned invalid JSON", error);
    }
  }

  private ArrayNode stringArray(JsonNode values) {
    ArrayNode result = mapper.createArrayNode();
    if (values != null && values.isArray()) {
      values.forEach(value -> result.add(value.asText()));
    }
    return result;
  }

  private String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException error) {
      throw new IllegalArgumentException("Unable to encode workflow context", error);
    }
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }

  private void requireRuntime() {
    if (model == null || clickHouseTools == null) {
      throw new IllegalStateException("SQL workflow tool runtime is not configured");
    }
  }

  public record SchemaHint(
      String database,
      String table,
      List<SchemaColumn> columns,
      String primaryKey,
      String partitionBy,
      String engine,
      String sortingKey) {}

  public record SchemaColumn(String name, String type) {}

  public record SqlContext(String database, String clickHouseUser) {}
}
