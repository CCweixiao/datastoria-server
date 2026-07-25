package io.datastoria.server.agent.application;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.datastoria.server.agent.domain.AgentPendingAction;
import io.datastoria.server.agent.domain.AgentRun;
import io.datastoria.server.agent.domain.AgentRunEvent;
import io.datastoria.server.agent.domain.AgentRunSkillPin;
import io.datastoria.server.agent.domain.AgentRunStatus;
import io.datastoria.server.agent.domain.CheckpointType;
import io.datastoria.server.agent.domain.PendingActionCheckpoint;
import io.datastoria.server.agent.domain.PendingActionStatus;
import io.datastoria.server.agent.domain.PendingActionType;
import io.datastoria.server.agent.domain.RunContext;
import io.datastoria.server.agent.domain.RunTransition;
import io.datastoria.server.agent.runtime.AgentRunCapabilities;
import io.datastoria.server.agent.runtime.AgentRuntimeConfig;
import io.datastoria.server.agent.runtime.AgentToolExecutionPolicy;
import io.datastoria.server.agent.runtime.ApprovalResumeRequest;
import io.datastoria.server.agent.runtime.ClickHouseAgentTools;
import io.datastoria.server.agent.runtime.HumanInteractionAgentTools;
import io.datastoria.server.agent.runtime.ModelAdapter;
import io.datastoria.server.agent.runtime.ModelAdapterProvider;
import io.datastoria.server.agent.runtime.QuestionResumeRequest;
import io.datastoria.server.agent.runtime.RepositoryAgentTools;
import io.datastoria.server.agent.runtime.SqlWorkflowAgentTools;
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
import io.datastoria.server.repository.AgentPendingActionRepository;
import io.datastoria.server.repository.AgentRevisionRepository;
import io.datastoria.server.repository.AgentRunRepository;
import io.datastoria.server.repository.AgentRunSkillRepository;
import io.datastoria.server.repository.AgentSkillRepository;
import io.datastoria.server.repository.AuditLogRepository;
import io.datastoria.server.repository.ChatMessageRepository;
import io.datastoria.server.repository.ChatSessionRepository;
import io.datastoria.server.repository.ModelRepository;
import io.datastoria.server.service.ClickHouseConnectionService;
import io.datastoria.server.service.RcaTemplateCatalog;
import io.datastoria.server.skill.BuiltinSkillProvisioner;
import io.datastoria.server.skill.SkillToolAvailability;

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
  private final AgentRunCreationService runCreationService;
  private final AgentRunRepository runRepository;
  private final AgentRunSkillRepository runSkillRepository;
  private final AgentPendingActionRepository pendingActionRepository;
  private final ChatSessionRepository sessionRepository;
  private final ChatMessageRepository messageRepository;
  private final ModelRepository modelRepository;
  private final AgentDefinitionRepository agentDefinitionRepository;
  private final AgentRevisionRepository agentRevisionRepository;
  private final AgentSkillRepository skillRepository;
  private final AuditLogRepository auditLogRepository;
  private final BuiltinSkillProvisioner builtinSkillProvisioner;
  private final SkillToolAvailability skillToolAvailability;
  private final ClickHouseConnectionService clickHouseConnectionService;
  private final RcaTemplateCatalog rcaTemplateCatalog;
  private final ModelAdapterProvider modelAdapterProvider;
  private final RunLifecycleRecorder lifecycleRecorder;
  private final Scheduler jdbcScheduler;
  private final ObjectMapper mapper;
  private final CheckpointStore checkpointStore;
  private final PendingActionCheckpointCodec pendingCheckpointCodec;
  private final String repositoryRoot;

  public ChatRunService(
      AgentRunService agentRunService,
      AgentRunCreationService runCreationService,
      AgentRunRepository runRepository,
      AgentRunSkillRepository runSkillRepository,
      AgentPendingActionRepository pendingActionRepository,
      ChatSessionRepository sessionRepository,
      ChatMessageRepository messageRepository,
      ModelRepository modelRepository,
      AgentDefinitionRepository agentDefinitionRepository,
      AgentRevisionRepository agentRevisionRepository,
      AgentSkillRepository skillRepository,
      AuditLogRepository auditLogRepository,
      BuiltinSkillProvisioner builtinSkillProvisioner,
      SkillToolAvailability skillToolAvailability,
      ClickHouseConnectionService clickHouseConnectionService,
      RcaTemplateCatalog rcaTemplateCatalog,
      ModelAdapterProvider modelAdapterProvider,
      RunLifecycleRecorder lifecycleRecorder,
      CheckpointStore checkpointStore,
      PendingActionCheckpointCodec pendingCheckpointCodec,
      ObjectMapper mapper,
      @Value("${datastoria.agent.repository-root:${user.dir}}") String repositoryRoot,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler) {
    this.agentRunService = agentRunService;
    this.runCreationService = runCreationService;
    this.runRepository = runRepository;
    this.runSkillRepository = runSkillRepository;
    this.pendingActionRepository = pendingActionRepository;
    this.sessionRepository = sessionRepository;
    this.messageRepository = messageRepository;
    this.modelRepository = modelRepository;
    this.agentDefinitionRepository = agentDefinitionRepository;
    this.agentRevisionRepository = agentRevisionRepository;
    this.skillRepository = skillRepository;
    this.auditLogRepository = auditLogRepository;
    this.builtinSkillProvisioner = builtinSkillProvisioner;
    this.skillToolAvailability = skillToolAvailability;
    this.clickHouseConnectionService = clickHouseConnectionService;
    this.rcaTemplateCatalog = rcaTemplateCatalog;
    this.modelAdapterProvider = modelAdapterProvider;
    this.lifecycleRecorder = lifecycleRecorder;
    this.checkpointStore = checkpointStore;
    this.pendingCheckpointCodec = pendingCheckpointCodec;
    this.mapper = mapper;
    this.repositoryRoot = repositoryRoot;
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

  /** Restores a permission-paused run and returns its continuation event stream. */
  public Mono<Flux<AgentRunEvent>> resume(String runId, Identity identity) {
    return Mono.fromCallable(() -> prepareResume(runId, identity))
        .subscribeOn(jdbcScheduler)
        .map(
            prepared -> {
              Flux<AgentRunEvent> events =
                  prepared.question() == null
                      ? agentRunService.resume(prepared.runRequest(), prepared.approval())
                      : agentRunService.resumeQuestion(prepared.runRequest(), prepared.question());
              RunContext rc = prepared.runRequest().context();
              RunMessageContext messageContext =
                  new RunMessageContext(
                      rc.tenantId(),
                      runId,
                      rc.userId(),
                      rc.sessionId(),
                      rc.messageId(),
                      rc.modelConfigId());
              return lifecycleRecorder.tap(messageContext, events);
            });
  }

  private PreparedResume prepareResume(String runId, Identity identity) {
    AgentRun run =
        runRepository
            .find(identity.tenantId(), runId)
            .filter(found -> identity.userId().equals(found.userId()))
            .orElseThrow(() -> new NotFoundException("AgentRun", runId));
    if (run.status() != AgentRunStatus.WAITING_INPUT) {
      throw new ResourceInUseException("AgentRun", runId);
    }
    var checkpointRow =
        checkpointStore
            .loadLatestRow(identity.tenantId(), runId)
            .filter(row -> row.checkpointType() == CheckpointType.PENDING_ACTION)
            .orElseThrow(() -> new NotFoundException("AgentCheckpoint", runId));
    PendingActionCheckpoint checkpoint =
        pendingCheckpointCodec.decode(
            new io.datastoria.server.agent.domain.CheckpointContent(
                checkpointRow.codecVersion(), checkpointRow.stateJson(), checkpointRow.checksum()));

    java.util.List<AgentPendingAction> actions =
        checkpoint.toolCalls().stream()
            .map(
                call ->
                    pendingActionRepository
                        .findByToolCall(
                            identity.tenantId(), identity.userId(), runId, call.toolCallId())
                        .orElseThrow(
                            () -> new NotFoundException("AgentPendingAction", call.actionId())))
            .toList();
    boolean question = actions.stream().anyMatch(a -> a.actionType() == PendingActionType.QUESTION);
    if (question
        && (actions.size() != 1 || actions.get(0).actionType() != PendingActionType.QUESTION)) {
      throw new IllegalStateException("Question checkpoints cannot contain mixed actions");
    }

    Model model =
        modelRepository
            .findById(run.modelId(), identity.tenantId())
            .orElseThrow(() -> new NotFoundException("Model", run.modelId()));
    ModelAdapter adapter;
    try {
      adapter = modelAdapterProvider.adapterFor(model, identity);
    } catch (RuntimeException ignored) {
      throw new ProviderOperationException(
          "PROVIDER_UNAVAILABLE", 503, "The selected model provider is unavailable");
    }
    AgentRuntimeConfig config = resolvePinnedAgentConfig(run, identity.tenantId());
    RunContext context =
        new RunContext(
            run.id(),
            run.tenantId(),
            run.userId(),
            run.sessionId(),
            run.messageId(),
            run.idempotencyKey(),
            run.agentRevisionId(),
            run.modelId(),
            run.createdAt());
    AgentRunCapabilities capabilities = resolvePinnedCapabilities(identity, run, context, adapter);
    java.util.List<ChatTurn> history = loadPersistedHistory(run.sessionId(), run.tenantId(), null);

    runRepository.transition(
        run.tenantId(), run.id(), AgentRunStatus.RUNNING, RunTransition.starting(Instant.now()));
    RunRequest request = new RunRequest(context, adapter, config, capabilities, history, "");
    if (question) {
      var action = actions.get(0);
      var call = checkpoint.toolCalls().get(0);
      if (action.status() != PendingActionStatus.RESPONDED) {
        throw new ResourceInUseException("AgentPendingAction", action.id());
      }
      return new PreparedResume(
          request,
          null,
          new QuestionResumeRequest(
              checkpointRow.sequence(),
              checkpoint.replyId(),
              action.id(),
              call.toolCallId(),
              call.toolName(),
              parseToolInput(call.inputJson()),
              parseQuestionResponse(action.responseJson())));
    }
    java.util.List<ApprovalResumeRequest.Decision> decisions =
        java.util.stream.IntStream.range(0, actions.size())
            .mapToObj(
                index -> {
                  var action = actions.get(index);
                  var call = checkpoint.toolCalls().get(index);
                  if (action.status() != PendingActionStatus.APPROVED
                      && action.status() != PendingActionStatus.DENIED) {
                    throw new ResourceInUseException("AgentPendingAction", action.id());
                  }
                  return new ApprovalResumeRequest.Decision(
                      call.toolCallId(),
                      call.toolName(),
                      parseToolInput(call.inputJson()),
                      action.status() == PendingActionStatus.APPROVED);
                })
            .toList();
    return new PreparedResume(
        request,
        new ApprovalResumeRequest(checkpointRow.sequence(), checkpoint.replyId(), decisions),
        null);
  }

  private AgentRuntimeConfig resolvePinnedAgentConfig(AgentRun run, String tenantId) {
    AgentRuntimeConfig base;
    if (BUILTIN_DEFAULT_REVISION.equals(run.agentRevisionId())) {
      base = AgentRuntimeConfig.minimal(DEFAULT_SYSTEM_PROMPT);
    } else {
      AgentRevision revision =
          agentRevisionRepository
              .findById(run.agentRevisionId(), tenantId)
              .orElseThrow(() -> new NotFoundException("AgentRevision", run.agentRevisionId()));
      base = AgentRuntimeConfig.minimal(revision.systemPrompt());
    }
    try {
      return AgentContextOptions.apply(
          base, run.inputSnapshotJson() == null ? null : mapper.readTree(run.inputSnapshotJson()));
    } catch (Exception ignored) {
      return base;
    }
  }

  private String runtimeOptionsSnapshot(JsonNode context) {
    if (context == null || !context.isObject()) {
      return "{}";
    }
    var safe = mapper.createObjectNode();
    for (String key : java.util.List.of("responseLanguage", "reasoningLevel", "outputReasoning")) {
      if (context.has(key)) {
        safe.set(key, context.get(key));
      }
    }
    return safe.toString();
  }

  private AgentRunCapabilities resolvePinnedCapabilities(
      Identity identity, AgentRun run, RunContext context, ModelAdapter adapter) {
    java.util.List<io.datastoria.server.domain.AgentSkill> selectedSkills =
        runSkillRepository.findByRun(run.tenantId(), run.id()).stream()
            .map(
                pin ->
                    skillRepository
                        .findRevision(
                            run.tenantId(), identity.userId(), pin.skillId(), pin.skillRevision())
                        .filter(skill -> pin.contentChecksum().equals(skill.bundleChecksum()))
                        .orElseThrow(
                            () -> new NotFoundException("AgentSkillRevision", pin.skillId())))
            .toList();
    java.util.List<io.agentscope.core.skill.AgentSkill> skills = toRuntimeSkills(selectedSkills);
    AgentToolExecutionPolicy toolPolicy =
        AgentToolExecutionPolicy.tracked(
            auditLogRepository, jdbcScheduler, identity, run.id(), run.connectionId());
    ClickHouseAgentTools clickHouseTools =
        new ClickHouseAgentTools(
            clickHouseConnectionService,
            run.connectionId(),
            identity,
            mapper,
            toolPolicy,
            rcaTemplateCatalog.findEnabled("high_part_count").orElse(null));
    Path configuredRoot =
        repositoryRoot == null || repositoryRoot.isBlank() ? null : Path.of(repositoryRoot);
    return new AgentRunCapabilities(
        skills,
        java.util.List.of(
            clickHouseTools,
            new SqlWorkflowAgentTools(
                adapter.modelFor(context), clickHouseTools, mapper, toolPolicy),
            new RepositoryAgentTools(configuredRoot, mapper, toolPolicy),
            new HumanInteractionAgentTools()));
  }

  private java.util.Map<String, Object> parseToolInput(String inputJson) {
    try {
      return mapper.readValue(
          inputJson,
          new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
    } catch (Exception e) {
      throw new IllegalStateException("Pending tool input is invalid", e);
    }
  }

  private String parseQuestionResponse(String responseJson) {
    try {
      JsonNode envelope = mapper.readTree(responseJson);
      if (!"responded".equals(envelope.path("status").asText()) || !envelope.has("response")) {
        throw new IllegalStateException("Question response envelope is invalid");
      }
      return mapper.writeValueAsString(envelope.get("response"));
    } catch (Exception e) {
      throw new IllegalStateException("Question response is invalid", e);
    }
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
    runRepository.findBySession(tenant, session.id()).stream()
        .filter(run -> !run.status().isTerminal())
        .findFirst()
        .ifPresent(
            active -> {
              throw new ResourceInUseException("AgentRun", active.id());
            });
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
    java.util.List<ChatAttachment> currentAttachments =
        attachments(req.message() == null ? null : req.message().path("parts"));
    if (req.userText().isBlank() && currentAttachments.isEmpty()) {
      throw PlainTextException.badRequest("message.parts must contain text or an image");
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
      adapter = modelAdapterProvider.adapterFor(modelConfig, identity);
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
            runtimeOptionsSnapshot(req.agentContext()),
            null,
            null,
            null,
            0L,
            now,
            null,
            now,
            now);
    ResolvedCapabilities resolvedCapabilities =
        resolveCapabilities(req, identity, runId, context, adapter);
    try {
      runCreationService.create(run, resolvedCapabilities.skillPins());
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
            AgentContextOptions.apply(agent.config(), req.agentContext()),
            resolvedCapabilities.capabilities(),
            loadHistory(req, tenant),
            enrichedUserText(req, tenant),
            currentAttachments);
    return new PreparedRun(runId, runRequest);
  }

  private ResolvedCapabilities resolveCapabilities(
      AgentChatRequest req,
      Identity identity,
      String runId,
      io.datastoria.server.agent.domain.RunContext context,
      ModelAdapter adapter) {
    builtinSkillProvisioner.provision(identity.tenantId());
    java.util.List<io.datastoria.server.domain.AgentSkill> selectedSkills =
        skillRepository.findVisible(identity.tenantId(), identity.userId(), false).stream()
            .filter(skill -> skillToolAvailability.isAvailable(skill.content(), skill.id()))
            .toList();
    java.util.List<io.agentscope.core.skill.AgentSkill> skills = toRuntimeSkills(selectedSkills);
    java.util.List<AgentRunSkillPin> pins =
        selectedSkills.stream()
            .map(
                skill ->
                    new AgentRunSkillPin(
                        identity.tenantId(),
                        runId,
                        skill.id(),
                        skill.revision(),
                        skill.bundleChecksum()))
            .toList();
    AgentToolExecutionPolicy toolPolicy =
        AgentToolExecutionPolicy.tracked(
            auditLogRepository, jdbcScheduler, identity, runId, req.connectionId());
    ClickHouseAgentTools clickHouseTools =
        new ClickHouseAgentTools(
            clickHouseConnectionService,
            req.connectionId(),
            identity,
            mapper,
            toolPolicy,
            rcaTemplateCatalog.findEnabled("high_part_count").orElse(null));
    Path configuredRoot =
        repositoryRoot == null || repositoryRoot.isBlank() ? null : Path.of(repositoryRoot);
    return new ResolvedCapabilities(
        new AgentRunCapabilities(
            skills,
            java.util.List.of(
                clickHouseTools,
                new SqlWorkflowAgentTools(
                    adapter.modelFor(context), clickHouseTools, mapper, toolPolicy),
                new RepositoryAgentTools(configuredRoot, mapper, toolPolicy),
                new HumanInteractionAgentTools())),
        pins);
  }

  private java.util.List<io.agentscope.core.skill.AgentSkill> toRuntimeSkills(
      java.util.List<io.datastoria.server.domain.AgentSkill> selectedSkills) {
    return selectedSkills.stream()
        .map(
            skill -> {
              java.util.Map<String, String> resources =
                  skillRepository
                      .findResources(skill.tenantId(), skill.id(), skill.revision())
                      .stream()
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
  }

  private java.util.List<ChatTurn> loadHistory(AgentChatRequest req, String tenant) {
    return loadPersistedHistory(req.sessionId(), tenant, req.messageId());
  }

  private java.util.List<ChatTurn> loadPersistedHistory(
      String sessionId, String tenant, String excludedMessageId) {
    MentionContextFormatter mentions = new MentionContextFormatter();
    java.util.List<ChatTurn> turns = new java.util.ArrayList<>();
    for (ChatMessage message : messageRepository.findBySession(sessionId, tenant)) {
      // The frontend persists the incoming user message before calling A01. It is appended below,
      // so exclude that exact id to avoid presenting the current turn twice.
      if (Objects.equals(message.id(), excludedMessageId)
          || (!"user".equals(message.role()) && !"assistant".equals(message.role()))) {
        continue;
      }
      String text = textContent(message);
      if ("user".equals(message.role())) {
        text = mentions.apply(text, metadata(message));
      }
      java.util.List<ChatAttachment> messageAttachments =
          "user".equals(message.role()) ? attachments(parts(message)) : java.util.List.of();
      java.util.List<ChatToolExchange> messageTools =
          "assistant".equals(message.role()) ? toolExchanges(parts(message)) : java.util.List.of();
      if (!text.isBlank() || !messageAttachments.isEmpty() || !messageTools.isEmpty()) {
        turns.add(new ChatTurn(message.role(), text, messageAttachments, messageTools));
      }
    }
    return turns;
  }

  private String enrichedUserText(AgentChatRequest req, String tenant) {
    MentionContextFormatter mentions = new MentionContextFormatter();
    for (ChatMessage message : messageRepository.findBySession(req.sessionId(), tenant)) {
      if ("user".equals(message.role()) && !Objects.equals(message.id(), req.messageId())) {
        mentions.apply(textContent(message), metadata(message));
      }
    }
    JsonNode metadata = req.message() == null ? null : req.message().path("metadata");
    return mentions.apply(req.userText(), metadata);
  }

  private JsonNode metadata(ChatMessage message) {
    if (message.metadataJson() == null || message.metadataJson().isBlank()) {
      return null;
    }
    try {
      return mapper.readTree(message.metadataJson());
    } catch (Exception ignored) {
      return null;
    }
  }

  private String textContent(ChatMessage message) {
    try {
      JsonNode parts = parts(message);
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

  private JsonNode parts(ChatMessage message) {
    try {
      return mapper.readTree(message.partsJson());
    } catch (Exception ignored) {
      return mapper.createArrayNode();
    }
  }

  private java.util.List<ChatToolExchange> toolExchanges(JsonNode parts) {
    if (parts == null || !parts.isArray()) {
      return java.util.List.of();
    }
    java.util.List<ChatToolExchange> exchanges = new java.util.ArrayList<>();
    for (JsonNode part : parts) {
      String type = part.path("type").asText("");
      if (!"dynamic-tool".equals(type) && !type.startsWith("tool-")) {
        continue;
      }
      String callId = part.path("toolCallId").asText("");
      String toolName = part.path("toolName").asText("");
      if (callId.isBlank() || toolName.isBlank() || !part.has("input")) {
        continue;
      }
      String state = part.path("state").asText("");
      JsonNode output = part.has("output") ? part.get("output") : part.get("errorText");
      if (output == null
          || (!"output-available".equals(state)
              && !"output-error".equals(state)
              && !"output-denied".equals(state))) {
        continue;
      }
      exchanges.add(
          new ChatToolExchange(
              callId,
              toolName,
              part.get("input").toString(),
              output.isTextual() ? output.asText() : output.toString(),
              !"output-available".equals(state)));
    }
    return java.util.List.copyOf(exchanges);
  }

  private java.util.List<ChatAttachment> attachments(JsonNode parts) {
    if (parts == null || !parts.isArray()) {
      return java.util.List.of();
    }
    java.util.List<ChatAttachment> attachments = new java.util.ArrayList<>();
    for (JsonNode part : parts) {
      if (!part.isObject() || !"file".equals(part.path("type").asText())) {
        continue;
      }
      String mediaType = part.path("mediaType").asText();
      String url = part.path("url").asText();
      if (!mediaType.startsWith("image/")
          || !(url.startsWith("data:image/")
              || url.startsWith("https://")
              || url.startsWith("http://"))) {
        throw PlainTextException.badRequest(
            "Only image file parts with data/http URLs are supported");
      }
      attachments.add(
          new ChatAttachment(
              mediaType, url, part.hasNonNull("filename") ? part.path("filename").asText() : null));
    }
    return java.util.List.copyOf(attachments);
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

  private record PreparedResume(
      RunRequest runRequest, ApprovalResumeRequest approval, QuestionResumeRequest question) {}

  private record ResolvedAgent(AgentRuntimeConfig config, String agentRevisionId) {}

  private record ResolvedCapabilities(
      AgentRunCapabilities capabilities, java.util.List<AgentRunSkillPin> skillPins) {}
}
