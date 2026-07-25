package io.datastoria.server.agent.testing;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;

import reactor.core.publisher.Flux;

/**
 * Deterministic, network-free fake {@link Model} shared by the AgentScope spike (P4.1) and the
 * runtime/adapter unit tests (P4.2+). It never opens a socket, reads no {@link
 * GenerateOptions#getApiKey() API key}, and is fully repeatable.
 *
 * <p>It emits a scripted sequence of {@link ChatResponse} frames — optional reasoning fragments,
 * text fragments, and a terminal usage/finishReason — records whether the returned {@link Flux} was
 * cancelled upstream, and how many tool schemas were offered. The same script yields byte-identical
 * events on every run.
 */
public final class FakeStreamModel implements Model {

  private final String modelName;
  private final List<ChatResponse> frames;
  private final Duration perFrameDelay;
  private final Throwable error;
  private final AtomicBoolean cancelled = new AtomicBoolean();
  private final AtomicInteger streamInvocations = new AtomicInteger();
  private final AtomicInteger lastToolCount = new AtomicInteger(-1);
  private volatile List<Msg> lastMessages = List.of();

  private FakeStreamModel(
      String modelName, List<ChatResponse> frames, Duration perFrameDelay, Throwable error) {
    this.modelName = modelName;
    this.frames = List.copyOf(frames);
    this.perFrameDelay = perFrameDelay;
    this.error = error;
  }

  /**
   * Returns the scripted frames as a {@link Flux}. The {@code .doOnCancel} hook records upstream
   * cancellation so tests can prove a disposed subscription propagates to the provider flux (the
   * reactive cancel path backing client disconnect).
   */
  @Override
  public Flux<ChatResponse> stream(
      List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
    streamInvocations.incrementAndGet();
    lastToolCount.set(tools.size());
    lastMessages = List.copyOf(messages);
    if (error != null) {
      return Flux.<ChatResponse>error(error).doOnCancel(() -> cancelled.set(true));
    }
    Flux<ChatResponse> flux = Flux.fromIterable(frames);
    if (!perFrameDelay.isZero()) {
      flux = flux.delayElements(perFrameDelay);
    }
    return flux.doOnCancel(() -> cancelled.set(true));
  }

  @Override
  public String getModelName() {
    return modelName;
  }

  /** Whether the model flux returned by the most recent {@link #stream} call was cancelled. */
  public boolean wasCancelled() {
    return cancelled.get();
  }

  /** Number of times AgentScope has invoked {@link #stream}. */
  public int streamInvocations() {
    return streamInvocations.get();
  }

  /** Number of tool schemas offered on the most recent {@link #stream} call. */
  public int lastToolCount() {
    return lastToolCount.get();
  }

  /** Messages supplied to the most recent model call, for multi-turn context assertions. */
  public List<Msg> lastMessages() {
    return lastMessages;
  }

  /** Reset cancellation/invocation counters so a fresh run is observable. */
  public void resetStats() {
    cancelled.set(false);
    streamInvocations.set(0);
    lastToolCount.set(-1);
    lastMessages = List.of();
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Fluent builder for scripting a deterministic model response. */
  public static final class Builder {
    private final List<ChatResponse> frames = new ArrayList<>();
    private String modelName = "fake-model";
    private Duration perFrameDelay = Duration.ZERO;
    private Throwable error;

    public Builder modelName(String modelName) {
      this.modelName = modelName;
      return this;
    }

    /** Adds an incremental reasoning (thinking) fragment as its own frame. */
    public Builder reasoning(String fragment) {
      frames.add(
          ChatResponse.builder()
              .content(List.of(ThinkingBlock.builder().thinking(fragment).build()))
              .build());
      return this;
    }

    /** Adds an incremental text fragment as its own frame. */
    public Builder text(String fragment) {
      frames.add(
          ChatResponse.builder()
              .content(List.of(TextBlock.builder().text(fragment).build()))
              .build());
      return this;
    }

    /** Terminal frame carrying usage and a finish reason (defaults to {@code "stop"}). */
    public Builder finish(int inputTokens, int outputTokens) {
      return finish(inputTokens, outputTokens, "stop");
    }

    public Builder finish(int inputTokens, int outputTokens, String finishReason) {
      ChatUsage usage =
          ChatUsage.builder().inputTokens(inputTokens).outputTokens(outputTokens).time(0.0).build();
      frames.add(
          ChatResponse.builder()
              .content(List.<ContentBlock>of())
              .usage(usage)
              .finishReason(finishReason)
              .metadata(Map.of())
              .build());
      return this;
    }

    /** Per-frame emission delay; non-zero only for the cancellation/disconnect scenarios. */
    public Builder perFrameDelay(Duration perFrameDelay) {
      this.perFrameDelay = perFrameDelay;
      return this;
    }

    /**
     * Makes {@code stream} signal the given error instead of emitting frames. Used to exercise the
     * RunFailed error path. The error message may contain sensitive-looking text to prove it is
     * sanitized before reaching the event stream.
     */
    public Builder error(Throwable error) {
      this.error = error;
      return this;
    }

    public FakeStreamModel build() {
      if (error == null && frames.isEmpty()) {
        throw new IllegalStateException("FakeStreamModel script must contain at least one frame");
      }
      return new FakeStreamModel(modelName, frames, perFrameDelay, error);
    }
  }
}
