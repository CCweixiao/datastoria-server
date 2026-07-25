package io.datastoria.server.agent.spike;

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
 * Deterministic, network-free fake {@link Model} for the P4.1 AgentScope compatibility spike
 * (ADR-0004).
 *
 * <p>It emits a scripted sequence of {@link ChatResponse} frames — optional reasoning fragments,
 * text fragments, and a terminal usage/finishReason — and records whether the returned {@link Flux}
 * was cancelled upstream. It never opens a socket, reads no {@link GenerateOptions#getApiKey() API
 * key}, and is fully repeatable: the same script yields byte-identical events on every run.
 *
 * <p>P4.1 scope only. P4.2 promotes this boundary to {@code io.datastoria.server.agent} as the
 * production {@code ModelAdapter} contract; this test-only copy exists to prove the AgentScope
 * streaming/cancel contract before any controller code is written.
 */
public final class FakeStreamModel implements Model {

  private final String modelName;
  private final List<ChatResponse> frames;
  private final Duration perFrameDelay;
  private final AtomicBoolean cancelled = new AtomicBoolean();
  private final AtomicInteger streamInvocations = new AtomicInteger();

  private FakeStreamModel(String modelName, List<ChatResponse> frames, Duration perFrameDelay) {
    this.modelName = modelName;
    this.frames = List.copyOf(frames);
    this.perFrameDelay = perFrameDelay;
  }

  /**
   * Returns the scripted frames as a {@link Flux}. The {@code .doOnCancel} hook records upstream
   * cancellation so the spike can prove a disposed {@code streamEvents} subscription propagates to
   * the model provider flux (the reactive cancel path that backs client disconnect).
   */
  @Override
  public Flux<ChatResponse> stream(
      List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
    streamInvocations.incrementAndGet();
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

  /** Reset cancellation/invocation counters so a fresh run is observable. */
  public void resetStats() {
    cancelled.set(false);
    streamInvocations.set(0);
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Fluent builder for scripting a deterministic model response. */
  public static final class Builder {
    private final List<ChatResponse> frames = new ArrayList<>();
    private String modelName = "fake-spike";
    private Duration perFrameDelay = Duration.ZERO;

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

    public FakeStreamModel build() {
      if (frames.isEmpty()) {
        throw new IllegalStateException("FakeStreamModel script must contain at least one frame");
      }
      return new FakeStreamModel(modelName, frames, perFrameDelay);
    }
  }
}
