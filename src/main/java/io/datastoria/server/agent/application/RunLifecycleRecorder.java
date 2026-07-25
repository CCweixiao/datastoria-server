package io.datastoria.server.agent.application;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionOperations;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.datastoria.server.agent.domain.AgentPendingAction;
import io.datastoria.server.agent.domain.AgentRunEvent;
import io.datastoria.server.agent.domain.AgentRunStatus;
import io.datastoria.server.agent.domain.CheckpointType;
import io.datastoria.server.agent.domain.PendingActionCheckpoint;
import io.datastoria.server.agent.domain.PendingActionStatus;
import io.datastoria.server.agent.domain.PendingActionType;
import io.datastoria.server.agent.domain.RunFailureCode;
import io.datastoria.server.agent.domain.RunTransition;
import io.datastoria.server.domain.ChatMessage;
import io.datastoria.server.repository.AgentPendingActionRepository;
import io.datastoria.server.repository.AgentRunRepository;
import io.datastoria.server.repository.ChatMessageRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Taps an {@link AgentRunEvent} stream and persists terminal state off the calling thread, so
 * blocking JDBC never runs on the Netty event loop:
 *
 * <ul>
 *   <li>{@link AgentRunEvent.UsageReported} → accumulates token usage across model calls.
 *   <li>{@link AgentRunEvent.TextDelta} → accumulates assistant text.
 *   <li>{@link AgentRunEvent.RunCompleted} → {@code SUCCEEDED} (with accumulated usage) AND
 *       persists the assistant message to {@code ds_chat_message} (tenant/user/session scoped,
 *       idempotent on message id — retries do not duplicate; empty text is skipped so no hollow
 *       message is left).
 *   <li>{@link AgentRunEvent.RunFailed} → {@code FAILED} with the sanitized {@link RunFailureCode}.
 *       No assistant message is written on failure.
 * </ul>
 *
 * <p>Cancellation ({@link AgentRunStatus#CANCELLED}) is handled separately by {@link
 * RunCancellationPersister} (the {@code AgentRunService} cancellation observer); a cancelled run
 * never reaches {@link AgentRunEvent.RunCompleted}, so no completed assistant message is left.
 *
 * <p>Terminal persistence runs on a bounded JDBC scheduler and completes before the terminal event
 * is forwarded. Thus receiving {@code finish} is a reliable refresh/replay boundary, while Netty
 * remains non-blocking. Terminal transitions reuse the P4.3 optimistic lock, so a late cancel
 * landing on an already-terminal run is a safe no-op. AgentScope-free.
 */
public final class RunLifecycleRecorder {

  private static final Logger log = LoggerFactory.getLogger(RunLifecycleRecorder.class);

  private final AgentRunRepository runRepository;
  private final ChatMessageRepository messageRepository;
  private final TransactionOperations transactions;
  private final Scheduler jdbcScheduler;
  private final ObjectMapper mapper;
  private final AgentPendingActionRepository pendingActions;
  private final CheckpointStore checkpoints;
  private final PendingActionCheckpointCodec pendingCheckpointCodec;

  public RunLifecycleRecorder(
      AgentRunRepository runRepository, ChatMessageRepository messageRepository) {
    this(
        runRepository,
        messageRepository,
        TransactionOperations.withoutTransaction(),
        Schedulers.boundedElastic(),
        new ObjectMapper(),
        null,
        null,
        null);
  }

  public RunLifecycleRecorder(
      AgentRunRepository runRepository,
      ChatMessageRepository messageRepository,
      Scheduler jdbcScheduler) {
    this(
        runRepository,
        messageRepository,
        TransactionOperations.withoutTransaction(),
        jdbcScheduler,
        new ObjectMapper(),
        null,
        null,
        null);
  }

  public RunLifecycleRecorder(
      AgentRunRepository runRepository,
      ChatMessageRepository messageRepository,
      TransactionOperations transactions,
      Scheduler jdbcScheduler) {
    this(
        runRepository,
        messageRepository,
        transactions,
        jdbcScheduler,
        new ObjectMapper(),
        null,
        null,
        null);
  }

  public RunLifecycleRecorder(
      AgentRunRepository runRepository,
      ChatMessageRepository messageRepository,
      TransactionOperations transactions,
      Scheduler jdbcScheduler,
      ObjectMapper mapper) {
    this(runRepository, messageRepository, transactions, jdbcScheduler, mapper, null, null, null);
  }

  public RunLifecycleRecorder(
      AgentRunRepository runRepository,
      ChatMessageRepository messageRepository,
      TransactionOperations transactions,
      Scheduler jdbcScheduler,
      ObjectMapper mapper,
      AgentPendingActionRepository pendingActions,
      CheckpointStore checkpoints,
      PendingActionCheckpointCodec pendingCheckpointCodec) {
    this.runRepository = runRepository;
    this.messageRepository = messageRepository;
    this.transactions = transactions;
    this.jdbcScheduler = jdbcScheduler;
    this.mapper = mapper;
    this.pendingActions = pendingActions;
    this.checkpoints = checkpoints;
    this.pendingCheckpointCodec = pendingCheckpointCodec;
  }

  /** Returns {@code events} unchanged, scheduling terminal persistence off the calling thread. */
  public Flux<AgentRunEvent> tap(RunMessageContext ctx, Flux<AgentRunEvent> events) {
    long[] usage = {0, 0, 0}; // inputTokens, outputTokens, cachedTokens
    StringBuilder text = new StringBuilder();
    java.util.LinkedHashMap<String, ObjectNode> tools = new java.util.LinkedHashMap<>();
    return events.concatMap(
        e -> {
          if (e instanceof AgentRunEvent.UsageReported u) {
            usage[0] += u.usage().inputTokens();
            usage[1] += u.usage().outputTokens();
            usage[2] += u.usage().cachedTokens();
          } else if (e instanceof AgentRunEvent.TextDelta d) {
            text.append(d.delta());
          } else if (e instanceof AgentRunEvent.ToolInputAvailable input) {
            ObjectNode part = mapper.createObjectNode();
            part.put("type", "dynamic-tool");
            part.put("toolCallId", input.toolCallId());
            part.put("toolName", input.toolName());
            part.put("state", "input-available");
            part.set("input", parseJson(input.inputJson()));
            tools.put(input.toolCallId(), part);
          } else if (e instanceof AgentRunEvent.ToolOutputAvailable output) {
            String state =
                output.denied()
                    ? "output-denied"
                    : output.error() ? "output-error" : "output-available";
            completeTool(
                tools,
                output.toolCallId(),
                state,
                output.error() || output.denied() ? null : output.outputJson(),
                output.error() || output.denied() ? output.outputJson() : null);
          } else if (e instanceof AgentRunEvent.ToolApprovalRequired approval) {
            return dispatch(() -> persistApproval(ctx, approval)).thenReturn(e);
          } else if (e instanceof AgentRunEvent.QuestionRequired question) {
            return dispatch(() -> persistQuestion(ctx, question)).thenReturn(e);
          } else if (e instanceof AgentRunEvent.RunCompleted) {
            String usageJson = usageJson(usage);
            String assistantText = text.toString();
            return dispatch(() -> persistCompletion(ctx, assistantText, tools, usageJson))
                .thenReturn(e);
          } else if (e instanceof AgentRunEvent.RunFailed f) {
            RunFailureCode code = parseFailureCode(f.code());
            return dispatch(
                    () ->
                        runRepository.transition(
                            ctx.tenantId(),
                            ctx.runId(),
                            AgentRunStatus.FAILED,
                            RunTransition.failing(Instant.now(), code)))
                .thenReturn(e);
          }
          return Mono.just(e);
        });
  }

  private void persistApproval(RunMessageContext ctx, AgentRunEvent.ToolApprovalRequired approval) {
    if (pendingActions == null || checkpoints == null || pendingCheckpointCodec == null) {
      throw new IllegalStateException("HITL persistence is not configured");
    }
    Instant now = Instant.now();
    PendingActionCheckpoint checkpoint =
        new PendingActionCheckpoint(
            approval.replyId(),
            approval.approvals().stream()
                .map(
                    item ->
                        new PendingActionCheckpoint.PendingToolCall(
                            item.actionId(), item.toolCallId(), item.toolName(), item.inputJson()))
                .toList());
    transactions.executeWithoutResult(
        ignored -> {
          runRepository.transition(
              ctx.tenantId(),
              ctx.runId(),
              AgentRunStatus.WAITING_INPUT,
              RunTransition.waitingForInput());
          for (AgentRunEvent.ToolApproval item : approval.approvals()) {
            pendingActions.create(
                ctx.userId(),
                new AgentPendingAction(
                    item.actionId(),
                    ctx.tenantId(),
                    ctx.runId(),
                    item.toolCallId(),
                    PendingActionType.APPROVAL,
                    approvalRequestJson(approval.replyId(), item),
                    null,
                    null,
                    PendingActionStatus.PENDING,
                    now.plus(java.time.Duration.ofMinutes(15)),
                    null,
                    null,
                    0,
                    now,
                    now));
          }
          checkpoints.save(
              ctx.tenantId(),
              ctx.runId(),
              approval.sequence(),
              CheckpointType.PENDING_ACTION,
              pendingCheckpointCodec.encode(checkpoint));
        });
  }

  private void persistQuestion(RunMessageContext ctx, AgentRunEvent.QuestionRequired question) {
    if (pendingActions == null || checkpoints == null || pendingCheckpointCodec == null) {
      throw new IllegalStateException("HITL persistence is not configured");
    }
    Instant now = Instant.now();
    PendingActionCheckpoint checkpoint =
        new PendingActionCheckpoint(
            question.replyId(),
            java.util.List.of(
                new PendingActionCheckpoint.PendingToolCall(
                    question.actionId(),
                    question.toolCallId(),
                    question.toolName(),
                    question.inputJson())));
    transactions.executeWithoutResult(
        ignored -> {
          runRepository.transition(
              ctx.tenantId(),
              ctx.runId(),
              AgentRunStatus.WAITING_INPUT,
              RunTransition.waitingForInput());
          pendingActions.create(
              ctx.userId(),
              new AgentPendingAction(
                  question.actionId(),
                  ctx.tenantId(),
                  ctx.runId(),
                  question.toolCallId(),
                  PendingActionType.QUESTION,
                  question.inputJson(),
                  null,
                  null,
                  PendingActionStatus.PENDING,
                  now.plus(java.time.Duration.ofMinutes(15)),
                  null,
                  null,
                  0,
                  now,
                  now));
          checkpoints.save(
              ctx.tenantId(),
              ctx.runId(),
              question.sequence(),
              CheckpointType.PENDING_ACTION,
              pendingCheckpointCodec.encode(checkpoint));
        });
  }

  private String approvalRequestJson(String replyId, AgentRunEvent.ToolApproval approval) {
    try {
      var request = mapper.createObjectNode();
      request.put("replyId", replyId);
      request.put("toolName", approval.toolName());
      request.set("input", mapper.readTree(approval.inputJson()));
      return mapper.writeValueAsString(request);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to encode approval request", e);
    }
  }

  private void persistCompletion(
      RunMessageContext ctx,
      String text,
      java.util.LinkedHashMap<String, ObjectNode> tools,
      String usageJson) {
    // A concurrent completion in the same session may choose the same next sequence. Retrying the
    // whole transaction recomputes the sequence after the winner commits, while rolling back the
    // run transition from the losing attempt.
    RuntimeException lastFailure = null;
    for (int attempt = 0; attempt < 3; attempt++) {
      try {
        transactions.executeWithoutResult(
            ignored -> {
              runRepository.transition(
                  ctx.tenantId(),
                  ctx.runId(),
                  AgentRunStatus.SUCCEEDED,
                  RunTransition.completing(Instant.now(), usageJson));
              persistAssistantMessage(ctx, text, tools, usageJson);
            });
        return;
      } catch (RuntimeException failure) {
        lastFailure = failure;
      }
    }
    throw lastFailure;
  }

  /**
   * Persists the completed assistant message, idempotent on {@code (tenant, session, messageId)}: a
   * retry with the same run reuses the same message id, so no duplicate is written. The next
   * session sequence is computed from existing rows; per the design (one active run per session), a
   * sequence collision is unexpected and is swallowed (the run is already SUCCEEDED).
   */
  private void persistAssistantMessage(
      RunMessageContext ctx,
      String text,
      java.util.LinkedHashMap<String, ObjectNode> tools,
      String usageJson) {
    if (text.isEmpty() && tools.isEmpty()) {
      return;
    }
    if (messageRepository.findById(ctx.messageId(), ctx.tenantId(), ctx.sessionId()).isPresent()) {
      return; // idempotent: already persisted by a prior completion of this run
    }
    long sequence =
        messageRepository.findBySession(ctx.sessionId(), ctx.tenantId()).stream()
                .mapToLong(ChatMessage::sequence)
                .max()
                .orElse(0L)
            + 1L;
    Instant now = Instant.now();
    ChatMessage message =
        new ChatMessage(
            ctx.messageId(),
            ctx.tenantId(),
            ctx.sessionId(),
            ctx.userId(),
            "assistant",
            partsJson(text, tools),
            "{\"usage\":" + usageJson + "}",
            sequence,
            now,
            now);
    messageRepository.save(message);
  }

  private Mono<Void> dispatch(Runnable jdbcTask) {
    return Mono.fromRunnable(
            () -> {
              try {
                jdbcTask.run();
              } catch (RuntimeException e) {
                // Optimistic-lock / illegal-transition / rare sequence-collision outcomes are
                // expected; log the class only — no prompt, provider text, or credential here.
                log.warn(
                    "Run terminal persistence did not apply: {}", e.getClass().getSimpleName());
              }
            })
        .subscribeOn(jdbcScheduler)
        .then();
  }

  private String usageJson(long[] usage) {
    try {
      var node = mapper.createObjectNode();
      node.put("inputTokens", usage[0]);
      node.put("outputTokens", usage[1]);
      node.put("cachedTokens", usage[2]);
      node.put("totalTokens", usage[0] + usage[1]);
      return mapper.writeValueAsString(node);
    } catch (JsonProcessingException e) {
      return "{}";
    }
  }

  private String partsJson(String text, java.util.LinkedHashMap<String, ObjectNode> tools) {
    try {
      ArrayNode parts = mapper.createArrayNode();
      for (ObjectNode tool : tools.values()) {
        parts.add(tool);
      }
      if (!text.isEmpty()) {
        var part = parts.addObject();
        part.put("type", "text");
        part.put("text", text);
      }
      return mapper.writeValueAsString(parts);
    } catch (JsonProcessingException e) {
      return "[{\"type\":\"text\",\"text\":\"\"}]";
    }
  }

  private void completeTool(
      java.util.LinkedHashMap<String, ObjectNode> tools,
      String toolCallId,
      String state,
      String outputJson,
      String errorText) {
    ObjectNode part =
        tools.computeIfAbsent(
            toolCallId,
            ignored -> {
              ObjectNode created = mapper.createObjectNode();
              created.put("type", "dynamic-tool");
              created.put("toolCallId", toolCallId);
              created.put("toolName", "unknown");
              return created;
            });
    part.put("state", state);
    if (outputJson != null) {
      part.set("output", parseJson(outputJson));
    }
    if (errorText != null) {
      part.put("errorText", errorText);
    }
  }

  private JsonNode parseJson(String value) {
    try {
      return mapper.readTree(value);
    } catch (Exception ignored) {
      return mapper.getNodeFactory().textNode(value);
    }
  }

  private static RunFailureCode parseFailureCode(String code) {
    try {
      return RunFailureCode.valueOf(code);
    } catch (IllegalArgumentException | NullPointerException ignored) {
      return RunFailureCode.AGENT_INTERNAL;
    }
  }
}
