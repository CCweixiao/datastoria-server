package io.datastoria.server.agent.application;

import java.time.Instant;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.datastoria.server.agent.domain.AgentRun;
import io.datastoria.server.agent.domain.AgentRunEvent;
import io.datastoria.server.agent.domain.AgentRunStatus;
import io.datastoria.server.agent.runtime.AgentRunCapabilities;
import io.datastoria.server.agent.runtime.AgentRuntimeConfig;
import io.datastoria.server.agent.runtime.ClickHouseAgentTools;
import io.datastoria.server.agent.runtime.ModelAdapter;
import io.datastoria.server.agent.runtime.ModelAdapterProvider;
import io.datastoria.server.api.compat.AgentChatRequest;
import io.datastoria.server.api.error.NotFoundException;
import io.datastoria.server.api.error.PlainTextException;
import io.datastoria.server.api.error.ProviderOperationException;
import io.datastoria.server.api.error.ResourceInUseException;
import io.datastoria.server.config.JdbcSchedulerConfig;
import io.datastoria.server.domain.AgentDefinition;
import io.datastoria.server.domain.AgentRevision;
import io.datastoria.server.domain.ChatMessage;
import io.datastoria.server.domain.ChatSession;
import io.datastoria.server.domain.Model;
import io.datastoria.server.domain.Ulid;
import io.datastoria.server.identity.Identity;
import io.datastoria.server.repository.AgentDefinitionRepository;
import io.datastoria.server.repository.AgentRevisionRepository;
import io.datastoria.server.repository.AgentRunRepository;
import io.datastoria.server.repository.AgentSkillRepository;
import io.datastoria.server.repository.ChatMessageRepository;
import io.datastoria.server.repository.ChatSessionRepository;
import io.datastoria.server.repository.ModelRepository;
import io.datastoria.server.service.ClickHouseConnectionService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/**
 * Orchestrates an A01 chat run on behalf of the controller: server-side resolution of session /
 * agent revision / model config, atomic {@code (tenant, user, idempotency_key)} dedup, run-record
 * creation, {@link AgentRunService#start} + {@link RunLifecycleRecorder} wiring. Returns the mapped
 * event stream; the controller encodes it.
 *
 * <p><b>Threading:</b> {@link #stream} runs the blocking resolution and run creation on the
 * dedicated {@link JdbcSchedulerConfig#JDBC_SCHEDULER jdbc scheduler} via {@code
 * Mono.fromCallable}, so none of it blocks the Netty event loop. The returned {@code
 * Flux<AgentRunEvent>} is subscribed once by WebFlux when writing the SSE response (no manual
 * subscribe), preserving the P4.2 single-use / auto-binding contract.
 *
 * <p>AgentScope-free: depends only on {@link AgentRunService}, repositories, and the {@link
 * ModelAdapterProvider} seam. Credentials are resolved inside the provider (P4.8), never here.
 */
@Service
public class ChatRunService {

  /** Fallback system prompt when no published agent revision is referenced. */
  static final String DEFAULT_SYSTEM_PROMPT = "You are a helpful assistant.";

  /**
   * Sentinel {@code agent_revision_id} for runs that use the built-in default prompt (no DB agent
   * revision). The column is NOT NULL and has no FK (logical reference only), so a non-null
   * placeholder records that no published revision was pinned.
   */
  static final String BUILTIN_DEFAULT_REVISION = "builtin-default";

  private final AgentRunService agentRunService;
  private final AgentRunRepository runRepository;
  private final ChatSessionRepository sessionRepository;
  private final ChatMessageRepository messageRepository;
  private final ModelRepository modelRepository;
  private final AgentDefinitionRepository agentDefinitionRepository;
  private final AgentRevisionRepository agentRevisionRepository;
  private final AgentSkillRepository skillRepository;
  private final ClickHouseConnectionService clickHouseConnectionService;
  private final ModelAdapterProvider modelAdapterProvider;
  private final RunLifecycleRecorder lifecycleRecorder;
  private final Scheduler jdbcScheduler;
  private final ObjectMapper mapper;

  public ChatRunService(
      AgentRunService agentRunService,
      AgentRunRepository runRepository,
      ChatSessionRepository sessionRepository,
      ChatMessageRepository messageRepository,
      ModelRepository modelRepository,
      AgentDefinitionRepository agentDefinitionRepository,
      AgentRevisionRepository agentRevisionRepository,
      AgentSkillRepository skillRepository,
      ClickHouseConnectionService clickHouseConnectionService,
      ModelAdapterProvider modelAdapterProvider,
      RunLifecycleRecorder lifecycleRecorder,
      ObjectMapper mapper,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler) {
    this.agentRunService = agentRunService;
    this.runRepository = runRepository;
    this.sessionRepository = sessionRepository;
    this.messageRepository = messageRepository;
    this.modelRepository = modelRepository;
    this.agentDefinitionRepository = agentDefinitionRepository;
    this.agentRevisionRepository = agentRevisionRepository;
    this.skillRepository = skillRepository;
    this.clickHouseConnectionService = clickHouseConnectionService;
    this.modelAdapterProvider = modelAdapterProvider;
    this.lifecycleRecorder = lifecycleRecorder;
    this.mapper = mapper;
    this.jdbcScheduler = jdbcScheduler;
  }

  /**
   * Prepares and starts a run, returning the mapped event stream (single-use). Terminal lifecycle
   * persistence is tapped off-thread; cancellation is handled by the {@link
   * RunCancellationPersister} wired into {@link AgentRunService}.
   */
  public Mono<Flux<AgentRunEvent>> stream(AgentChatRequest req, Identity identity) {
    return Mono.fromCallable(() -> prepareRun(req, identity))
        .subscribeOn(jdbcScheduler)
        .map(
            prepared -> {
              Flux<AgentRunEvent> events = agentRunService.start(prepared.runRequest());
              io.datastoria.server.agent.domain.RunContext rc = prepared.runRequest().context();
              RunMessageContext ctx =
                  new RunMessageContext(
                      rc.tenantId(),
                      prepared.runId(),
                      rc.userId(),
                      rc.sessionId(),
                      rc.messageId(),
                      rc.modelConfigId());
              return lifecycleRecorder.tap(ctx, events);
            });
  }

  private PreparedRun prepareRun(AgentChatRequest req, Identity identity) {
    String tenant = identity.tenantId();
    String user = identity.userId();
    Objects.requireNonNull(tenant, "tenant");
    Objects.requireNonNull(user, "user");

    if (req.sessionId() == null || req.sessionId().isBlank()) {
      throw PlainTextException.badRequest("sessionId is required");
    }
    if (req.continuation()) {
      throw PlainTextException.badRequest("continuation runs are not supported yet");
    }
    // Validate session ownership (tenant + user). Sessions are created via the A03 endpoint first.
    ChatSession session =
        sessionRepository
            .findById(req.sessionId(), tenant, user)
            .orElseThrow(() -> new NotFoundException("ChatSession", req.sessionId()));
    if (req.connectionId() == null || req.connectionId().isBlank()) {
      throw PlainTextException.badRequest("connectionId is required");
    }
    if (!req.connectionId().equals(session.connectionId())) {
      // The session lookup is already tenant + user scoped. Requiring its pinned connection avoids
      // letting a caller relabel the run with an unrelated (or another tenant's) connection id.
      throw new NotFoundException("Connection", req.connectionId());
    }
    if (!"user".equals(req.role())) {
      throw PlainTextException.badRequest("message.role must be user");
    }
    if (req.userText().isBlank()) {
      throw PlainTextException.badRequest("message.parts must contain text");
    }

    Model modelConfig = resolveModel(req, tenant);
    if (!modelConfig.enabled()) {
      throw PlainTextException.badRequest("Selected model is disabled");
    }

    ResolvedAgent agent = resolveAgent(req, tenant);
    // Resolve the server-side adapter before inserting the RUNNING row. Provider configuration or
    // credential failures must not leave a run that can never emit a terminal lifecycle event.
    ModelAdapter adapter;
    try {
      adapter = modelAdapterProvider.adapterFor(modelConfig);
    } catch (RuntimeException ignored) {
      // Adapter initialization can touch decrypted server-side credentials. Do not retain the
      // original exception as a cause: the global error logger must not receive provider secrets.
      throw new ProviderOperationException(
          "PROVIDER_UNAVAILABLE", 503, "The selected model provider is unavailable");
    }

    String idempotencyKey = normalize(req.clientRequestId());
    if (idempotencyKey != null) {
      // Fast path: a previous request for this key already won.
      runRepository
          .findByIdempotencyKey(tenant, user, idempotencyKey)
          .ifPresent(existing -> rejectDuplicate(existing.id(), existing.status()));
    }

    String runId = Ulid.next();
    // The incoming message id belongs to the user's message. The stream start id and persisted
    // assistant reply must be distinct, matching the existing Node A01 behaviour.
    String messageId = Ulid.next();
    io.datastoria.server.agent.domain.RunContext context =
        new io.datastoria.server.agent.domain.RunContext(
            runId,
            tenant,
            user,
            session.id(),
            messageId,
            idempotencyKey,
            agent.agentRevisionId(),
            modelConfig.id(),
            Instant.now());
    Instant now = Instant.now();
    AgentRun run =
        new AgentRun(
            runId,
            tenant,
            user,
            session.id(),
            messageId,
            agent.agentRevisionId(),
            modelConfig.id(),
            AgentRunStatus.RUNNING,
            idempotencyKey,
            idempotencyKey,
            req.connectionId(),
            null,
            null,
            null,
            null,
            0L,
            now,
            null,
            now,
            now);
    try {
      runRepository.create(run);
    } catch (RuntimeException conflict) {
      // The UNIQUE(tenant,user,idempotency_key) constraint is the atomic arbiter for a concurrent
      // duplicate; lookup-then-create alone would race. Re-resolve and reject the loser.
      if (idempotencyKey != null) {
        runRepository
            .findByIdempotencyKey(tenant, user, idempotencyKey)
            .ifPresent(winner -> rejectDuplicate(winner.id(), winner.status()));
      }
      throw conflict;
    }

    RunRequest runRequest =
        new RunRequest(
            context,
            adapter,
            agent.config(),
            resolveCapabilities(req, identity),
            loadHistory(req, tenant),
            req.userText());
    return new PreparedRun(runId, runRequest);
  }

  private AgentRunCapabilities resolveCapabilities(AgentChatRequest req, Identity identity) {
    java.util.List<io.agentscope.core.skill.AgentSkill> skills =
        skillRepository.findVisible(identity.tenantId(), identity.userId(), false).stream()
            .map(
                skill -> {
                  java.util.Map<String, String> resources =
                      skillRepository.findResources(skill.tenantId(), skill.id()).stream()
                          .collect(
                              java.util.stream.Collectors.toMap(
                                  io.datastoria.server.domain.AgentSkillResource::path,
                                  io.datastoria.server.domain.AgentSkillResource::content));
                  return io.agentscope.core.skill.AgentSkill.builder()
                      .name(skill.id())
                      .description(skill.id())
                      .skillContent(skill.content())
                      .resources(resources)
                      .source("datastoria-database")
                      .build();
                })
            .toList();
    return new AgentRunCapabilities(
        skills,
        new ClickHouseAgentTools(clickHouseConnectionService, req.connectionId(), identity));
  }

  private java.util.List<ChatTurn> loadHistory(AgentChatRequest req, String tenant) {
    return messageRepository.findBySession(req.sessionId(), tenant).stream()
        // The frontend persists the incoming user message before calling A01. It is appended below,
        // so exclude that exact id to avoid presenting the current turn twice.
        .filter(message -> !Objects.equals(message.id(), req.messageId()))
        .filter(message -> "user".equals(message.role()) || "assistant".equals(message.role()))
        .map(message -> new ChatTurn(message.role(), textContent(message)))
        .filter(turn -> !turn.text().isBlank())
        .toList();
  }

  private String textContent(ChatMessage message) {
    try {
      JsonNode parts = mapper.readTree(message.partsJson());
      if (!parts.isArray()) {
        return "";
      }
      StringBuilder text = new StringBuilder();
      for (JsonNode part : parts) {
        if (part.isObject() && "text".equals(part.path("type").asText())) {
          if (text.length() > 0) {
            text.append('\n');
          }
          text.append(part.path("text").asText(""));
        }
      }
      return text.toString();
    } catch (Exception ignored) {
      // Persisted malformed parts must not leak or prevent a new run; repository/API validation
      // owns data integrity. Unknown/non-text UI parts are intentionally not sent in text-only P4.
      return "";
    }
  }

  /** Rejects a duplicate idempotent submission; behavior is fixed per run status. */
  private static RuntimeException rejectDuplicate(String runId, AgentRunStatus status) {
    if (status == AgentRunStatus.RUNNING
        || status == AgentRunStatus.QUEUED
        || status == AgentRunStatus.WAITING_INPUT) {
      throw new ResourceInUseException("AgentRun", runId); // 409: an agent is already active
    }
    // Terminal runs cannot be replayed yet (event replay is P4.8).
    throw new ResourceInUseException("AgentRun", runId);
  }

  private Model resolveModel(AgentChatRequest req, String tenant) {
    if (req.modelConfigId() != null && !req.modelConfigId().isBlank()) {
      return modelRepository
          .findById(req.modelConfigId(), tenant)
          .orElseThrow(() -> new NotFoundException("Model", req.modelConfigId()));
    }
    String modelKey = req.modelId();
    if (modelKey == null || modelKey.isBlank()) {
      throw PlainTextException.badRequest("modelConfigId or model.modelId is required");
    }
    // Best-effort legacy {provider,modelId} resolution by model key; modelConfigId is preferred.
    return modelRepository.findEnabled(tenant).stream()
        .filter(m -> modelKey.equals(m.modelKey()))
        .findFirst()
        .orElseThrow(() -> new NotFoundException("Model", modelKey));
  }

  private ResolvedAgent resolveAgent(AgentChatRequest req, String tenant) {
    String agentId = req.agentId();
    if (agentId == null || agentId.isBlank()) {
      return new ResolvedAgent(
          AgentRuntimeConfig.minimal(DEFAULT_SYSTEM_PROMPT), BUILTIN_DEFAULT_REVISION);
    }
    AgentDefinition def =
        agentDefinitionRepository
            .findById(agentId, tenant)
            .orElseThrow(() -> new NotFoundException("Agent", agentId));
    String revisionId = def.publishedRevisionId();
    if (revisionId == null) {
      return new ResolvedAgent(
          AgentRuntimeConfig.minimal(DEFAULT_SYSTEM_PROMPT), BUILTIN_DEFAULT_REVISION);
    }
    AgentRevision revision =
        agentRevisionRepository
            .findById(revisionId, tenant)
            .orElseThrow(() -> new NotFoundException("AgentRevision", revisionId));
    return new ResolvedAgent(AgentRuntimeConfig.minimal(revision.systemPrompt()), revision.id());
  }

  private static String normalize(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private record PreparedRun(String runId, RunRequest runRequest) {}

  private record ResolvedAgent(AgentRuntimeConfig config, String agentRevisionId) {}
}
