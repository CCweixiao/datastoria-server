package io.github.ccweixiao.datastoria.agent.runtime;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.github.ccweixiao.datastoria.common.dto.approval.ApprovalTransitionRequest;
import io.github.ccweixiao.datastoria.common.dto.approval.DdlApprovalPrepareRequest;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.service.approval.ApprovalCommandService;

import reactor.core.publisher.Mono;

/** Run-scoped approval tools. These tools never execute DDL or accept approval decisions. */
public final class ApprovalAgentTools {

  private final ApprovalCommandService service;
  private final String connectionId;
  private final String sessionId;
  private final String runId;
  private final Identity identity;
  private final ObjectMapper mapper;
  private final AgentToolExecutionPolicy executionPolicy;

  public ApprovalAgentTools(
      ApprovalCommandService service,
      String connectionId,
      String sessionId,
      String runId,
      Identity identity,
      ObjectMapper mapper,
      AgentToolExecutionPolicy executionPolicy) {
    this.service = service;
    this.connectionId = connectionId;
    this.sessionId = sessionId;
    this.runId = runId;
    this.identity = identity;
    this.mapper = mapper;
    this.executionPolicy = executionPolicy;
  }

  @Tool(
      name = "list_approval_work_order_types",
      description =
          "List the DDL work order types currently enabled for this run's ClickHouse connection.",
      readOnly = true)
  public Mono<String> listTypes() {
    return executionPolicy.guard(
        "list_approval_work_order_types",
        service.listTypes(connectionId, identity).map(this::json));
  }

  @Tool(
      name = "prepare_ddl_approval",
      description =
          "Compile, validate, and save a DDL approval draft. This never executes DDL or submits approval.",
      readOnly = false)
  public Mono<String> prepare(
      @ToolParam(name = "work_order_type_key", required = true) String workOrderTypeKey,
      @ToolParam(name = "title", required = true) String title,
      @ToolParam(name = "summary", required = false) String summary,
      @ToolParam(
              name = "intent",
              required = true,
              description = "Structured intent matching the selected work order type")
          Map<String, Object> intent) {
    DdlApprovalPrepareRequest request =
        new DdlApprovalPrepareRequest(
            connectionId,
            workOrderTypeKey,
            title,
            summary,
            mapper.valueToTree(intent),
            sessionId,
            runId);
    return executionPolicy.guard(
        "prepare_ddl_approval", service.prepare(request, identity).map(this::json));
  }

  @Tool(
      name = "submit_ddl_approval",
      description =
          "Submit an unchanged saved DDL draft for administrator review. Requires explicit tool approval and never executes DDL.",
      readOnly = false)
  public Mono<String> submit(
      @ToolParam(name = "draft_id", required = true) String draftId,
      @ToolParam(name = "expected_revision", required = true) Long expectedRevision,
      @ToolParam(name = "expected_content_digest", required = true) String contentDigest) {
    return executionPolicy.guard(
        "submit_ddl_approval",
        service
            .submit(
                draftId,
                new ApprovalTransitionRequest(expectedRevision, contentDigest, null),
                identity)
            .map(this::json));
  }

  @Tool(
      name = "get_approval_status",
      description = "Read the latest status and safe details for one visible work order.",
      readOnly = true)
  public Mono<String> status(@ToolParam(name = "request_id", required = true) String requestId) {
    return executionPolicy.guard(
        "get_approval_status", service.detail(requestId, identity).map(this::json));
  }

  private String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to encode approval tool result", exception);
    }
  }
}
