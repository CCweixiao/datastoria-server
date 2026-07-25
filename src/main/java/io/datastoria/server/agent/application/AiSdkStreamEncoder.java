package io.datastoria.server.agent.application;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.datastoria.server.agent.domain.AgentRunEvent;
import io.datastoria.server.agent.domain.RunFailureCode;

import reactor.core.publisher.Flux;

/**
 * Encodes DataStoria {@link AgentRunEvent}s into the AI SDK v6 <i>UI Message Stream</i> wire format
 * consumed by the existing {@code @ai-sdk/react} frontend (Node A01, {@code
 * createUIMessageStreamResponse}).
 *
 * <p><b>AgentScope-free.</b> Operates only on {@link AgentRunEvent} and string frames; never
 * references {@code io.agentscope.*}. The controller (P4.6) and tests depend solely on this type.
 *
 * <p><b>Incremental.</b> {@link #encode(AgentRunEvent)} returns the frame(s) for a single event —
 * it does not collect the whole stream. {@link #encode(Flux)} maps per event via {@code concatMap}
 * and appends the terminator, so frames are emitted as events arrive.
 *
 * <h2>Frame + sequence rules (frozen against {@code docs/api/stream-protocol.md} §3 and the
 * fixtures in {@code docs/fixtures/stream})</h2>
 *
 * <ul>
 *   <li>Every chunk is {@code "data: " + compactJSON + "\n\n"} (Jackson escaping of {@code " \ \n
 *       \t} and control chars). The stream ends with {@code "data: [DONE]\n\n"}.
 *   <li>{@code RunStarted} → {@code start}{messageId} then {@code start-step}.
 *   <li>{@code ReasoningBlock{Started,Delta,Ended}} → {@code reasoning-start/delta/end}, sharing
 *       one opaque part id per block.
 *   <li>{@code TextBlock{Started,Delta,Ended}} → {@code text-start/delta/end}, sharing one part id.
 *   <li>{@code UsageReported} → emits nothing; usage is accumulated across model calls and carried
 *       on the {@code finish} chunk's {@code messageMetadata.usage}.
 *   <li>{@code RunCompleted} → {@code finish-step} then {@code finish}{finishReason="stop",
 *       messageMetadata.usage}.
 *   <li>{@code RunFailed} → {@code error}{errorText = fixed safe message derived from its code}.
 *       The event's message is deliberately ignored, so an incorrectly constructed upstream event
 *       still cannot expose a provider exception, prompt, or credential.
 *   <li>{@code RunCancelled} → {@code abort}{reason}. Honors the client abort semantic: the encoder
 *       only produces this frame <em>if</em> a {@code RunCancelled} reaches it; a disposed
 *       subscription (client disconnect) never receives one, so nothing is force-written
 *       downstream.
 * </ul>
 *
 * <h2>Usage wire shape</h2>
 *
 * Matches the actual Node A01 output ({@code normalizeUsage}/{@code sumTokenUsage} in {@code
 * token-usage-utils.ts}): {@code {inputTokens, inputTokenDetails: {noCacheTokens, cacheReadTokens,
 * cacheWriteTokens}, outputTokens, outputTokenDetails: {textTokens, reasoningTokens},
 * totalTokens}}. The hand-built fixtures in {@code docs/fixtures/stream} use the deprecated {@code
 * promptTokens}/{@code completionTokens} naming; the encoder follows the live frontend behavior
 * (see P4.5 report).
 *
 * <p><b>Stateful / per-run.</b> Holds part-id counters and accumulated usage; not thread-safe.
 * {@link #encode(Flux)} creates a fresh encoder per subscription.
 */
public final class AiSdkStreamEncoder {

  /** Abort reason emitted on {@link AgentRunEvent.RunCancelled}, matching {@code cancel.jsonl}. */
  static final String ABORT_REASON = "client_disconnect";

  private final ObjectMapper mapper;
  private boolean started;
  private int textSeq;
  private int reasoningSeq;
  private String currentTextId;
  private String currentReasoningId;
  private long inputTokens;
  private long outputTokens;
  private long cachedTokens;
  private String title;

  public AiSdkStreamEncoder() {
    this(new ObjectMapper());
  }

  /**
   * Sets a provisional title emitted on the {@code finish} chunk's {@code messageMetadata.title}.
   */
  public AiSdkStreamEncoder withTitle(String title) {
    this.title = title;
    return this;
  }

  public AiSdkStreamEncoder(ObjectMapper mapper) {
    // An application ObjectMapper may have pretty printing enabled. Copy it so encoder-local
    // settings cannot mutate the shared bean, then force compact one-line JSON required by SSE.
    this.mapper = mapper.copy().disable(SerializationFeature.INDENT_OUTPUT);
  }

  /** Encodes one event into zero or more SSE frames, updating internal state. */
  public List<String> encode(AgentRunEvent event) {
    List<String> frames = new ArrayList<>(2);
    if (event instanceof AgentRunEvent.RunStarted e) {
      if (!started) {
        started = true;
        ObjectNode start = mapper.createObjectNode();
        start.put("type", "start");
        start.put("messageId", e.messageId());
        frames.add(frame(start));
        ObjectNode startStep = mapper.createObjectNode();
        startStep.put("type", "start-step");
        frames.add(frame(startStep));
      }
    } else if (event instanceof AgentRunEvent.ReasoningBlockStarted) {
      currentReasoningId = "rsn-" + (++reasoningSeq);
      frames.add(frame(partMarker("reasoning-start", currentReasoningId)));
    } else if (event instanceof AgentRunEvent.ReasoningDelta e) {
      if (currentReasoningId == null) {
        currentReasoningId = "rsn-" + (++reasoningSeq);
        frames.add(frame(partMarker("reasoning-start", currentReasoningId)));
      }
      frames.add(frame(delta("reasoning-delta", currentReasoningId, e.delta())));
    } else if (event instanceof AgentRunEvent.ReasoningBlockEnded) {
      if (currentReasoningId != null) {
        frames.add(frame(partMarker("reasoning-end", currentReasoningId)));
        currentReasoningId = null;
      }
    } else if (event instanceof AgentRunEvent.TextBlockStarted) {
      currentTextId = "txt-" + (++textSeq);
      frames.add(frame(partMarker("text-start", currentTextId)));
    } else if (event instanceof AgentRunEvent.TextDelta e) {
      if (currentTextId == null) {
        currentTextId = "txt-" + (++textSeq);
        frames.add(frame(partMarker("text-start", currentTextId)));
      }
      frames.add(frame(delta("text-delta", currentTextId, e.delta())));
    } else if (event instanceof AgentRunEvent.TextBlockEnded) {
      if (currentTextId != null) {
        frames.add(frame(partMarker("text-end", currentTextId)));
        currentTextId = null;
      }
    } else if (event instanceof AgentRunEvent.UsageReported e) {
      this.inputTokens += e.usage().inputTokens();
      this.outputTokens += e.usage().outputTokens();
      this.cachedTokens += e.usage().cachedTokens();
    } else if (event instanceof AgentRunEvent.RunCompleted) {
      frames.add(frame(simple("finish-step")));
      ObjectNode finish = mapper.createObjectNode();
      finish.put("type", "finish");
      finish.put("finishReason", "stop");
      ObjectNode metadata = finish.putObject("messageMetadata");
      metadata.set("usage", usageNode());
      if (title != null && !title.isBlank()) {
        metadata.put("title", title);
      }
      frames.add(frame(finish));
    } else if (event instanceof AgentRunEvent.RunFailed e) {
      ObjectNode error = mapper.createObjectNode();
      error.put("type", "error");
      error.put("errorText", safeFailureMessage(e.code()));
      frames.add(frame(error));
    } else if (event instanceof AgentRunEvent.RunCancelled) {
      ObjectNode abort = mapper.createObjectNode();
      abort.put("type", "abort");
      abort.put("reason", ABORT_REASON);
      frames.add(frame(abort));
    }
    return frames;
  }

  /** The terminating frame. Always {@code "data: [DONE]\n\n"}. */
  public String done() {
    return "data: [DONE]\n\n";
  }

  /**
   * Incrementally encodes a run's event stream into SSE frames and appends the {@code [DONE]}
   * terminator. A fresh encoder is used per subscription, so the returned Flux is safe to subscribe
   * once per run (mirrors the single-use {@code AgentRunService} Flux).
   */
  public static Flux<String> encode(Flux<AgentRunEvent> events) {
    return encode(events, null);
  }

  /**
   * Incrementally encodes a run's event stream into SSE frames and appends the {@code [DONE]}
   * terminator, optionally injecting a provisional {@code title} on the {@code finish} chunk. A
   * fresh encoder is used per subscription, so the returned Flux is safe to subscribe once per run.
   */
  public static Flux<String> encode(Flux<AgentRunEvent> events, String title) {
    return Flux.defer(
        () -> {
          AiSdkStreamEncoder encoder = new AiSdkStreamEncoder().withTitle(title);
          return events
              .concatMap(e -> Flux.fromIterable(encoder.encode(e)))
              .concatWith(Flux.just(encoder.done()));
        });
  }

  private ObjectNode simple(String type) {
    ObjectNode node = mapper.createObjectNode();
    node.put("type", type);
    return node;
  }

  private ObjectNode partMarker(String type, String id) {
    ObjectNode node = mapper.createObjectNode();
    node.put("type", type);
    node.put("id", id);
    return node;
  }

  private ObjectNode delta(String type, String id, String text) {
    ObjectNode node = mapper.createObjectNode();
    node.put("type", type);
    node.put("id", id);
    node.put("delta", text);
    return node;
  }

  /** Builds the AI SDK v6 {@code LanguageModelUsage}-shaped object (matches Node A01 output). */
  private ObjectNode usageNode() {
    ObjectNode usage = mapper.createObjectNode();
    usage.put("inputTokens", inputTokens);
    ObjectNode inDetails = usage.putObject("inputTokenDetails");
    inDetails.put("noCacheTokens", Math.max(0L, inputTokens - cachedTokens));
    inDetails.put("cacheReadTokens", cachedTokens);
    inDetails.put("cacheWriteTokens", 0);
    usage.put("outputTokens", outputTokens);
    ObjectNode outDetails = usage.putObject("outputTokenDetails");
    outDetails.put("textTokens", 0);
    outDetails.put("reasoningTokens", 0);
    usage.put("totalTokens", inputTokens + outputTokens);
    return usage;
  }

  private static String safeFailureMessage(String code) {
    try {
      return RunFailureCode.valueOf(code).safeMessage();
    } catch (IllegalArgumentException | NullPointerException ignored) {
      return RunFailureCode.AGENT_INTERNAL.safeMessage();
    }
  }

  private String frame(ObjectNode chunk) {
    try {
      return "data: " + mapper.writeValueAsString(chunk) + "\n\n";
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize stream chunk", e);
    }
  }
}
