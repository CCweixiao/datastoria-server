package io.datastoria.server.agent.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.datastoria.server.agent.domain.AgentRunEvent;
import io.datastoria.server.agent.domain.RunContext;
import io.datastoria.server.agent.domain.RunFailureCode;
import io.datastoria.server.agent.runtime.AgentRuntimeConfig;
import io.datastoria.server.agent.runtime.CancellationRegistry;
import io.datastoria.server.agent.runtime.HarnessAgentFactory;
import io.datastoria.server.agent.testing.FakeModelAdapter;
import io.datastoria.server.agent.testing.FakeStreamModel;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;

/**
 * Exercises the {@link AgentRunService} skeleton end-to-end with a deterministic fake model: normal
 * streaming + completion, sanitized RunFailed on model error (no leak of raw exception text), and
 * server-initiated cancel that stops the provider flux — including tenant isolation on cancel.
 */
class AgentRunServiceTest {

  private static final Set<String> SAFE_MESSAGES =
      Arrays.stream(RunFailureCode.values())
          .map(RunFailureCode::safeMessage)
          .collect(Collectors.toUnmodifiableSet());

  private static RunContext ctx(String runId) {
    return new RunContext(runId, "t1", "u1", "s", "m", "c", "a", "mc", Instant.EPOCH);
  }

  private static AgentRunService newService() {
    // Synchronous cleanup executor so unregister/close happen deterministically at termination.
    return new AgentRunService(
        new HarnessAgentFactory(), new CancellationRegistry(), Runnable::run);
  }

  private static List<AgentRunEvent> startAndCollect(
      AgentRunService service, RunContext ctx, FakeStreamModel model) {
    return service
        .start(
            new RunRequest(
                ctx, new FakeModelAdapter(model), AgentRuntimeConfig.minimal("sys"), "hi"))
        .collectList()
        .block();
  }

  @Test
  void startStreamsMappedEventsThenCompletesAndUnregisters() {
    FakeStreamModel model = FakeStreamModel.builder().text("Hello").text("!").finish(2, 3).build();
    AgentRunService service = newService();
    RunContext context = ctx("run-1");

    List<AgentRunEvent> events = startAndCollect(service, context, model);

    assertThat(events).isNotNull();
    assertThat(events.stream().map(e -> e.getClass().getSimpleName()).toList())
        .containsExactly(
            "RunStarted",
            "TextBlockStarted",
            "TextDelta",
            "TextDelta",
            "TextBlockEnded",
            "UsageReported",
            "RunCompleted");
    assertThat(service.isActive("run-1"))
        .as("run unregistered after terminal signal (synchronous cleanup executor)")
        .isFalse();
  }

  @Test
  void modelErrorBecomesSanitizedRunFailedWithoutLeakingRawText() {
    // An exception whose message mimics a leaked credential/prompt fragment.
    FakeStreamModel model =
        FakeStreamModel.builder()
            .error(new RuntimeException("leak sk-SECRET-123 raw prompt fragment"))
            .build();
    AgentRunService service = newService();
    RunContext context = ctx("run-2");

    // collectList().block() would throw if onError escaped; success here proves errors are
    // consumed.
    List<AgentRunEvent> events = startAndCollect(service, context, model);

    assertThat(events).isNotNull();
    assertThat(events).isNotEmpty();
    AgentRunEvent last = events.get(events.size() - 1);
    assertThat(last).isInstanceOf(AgentRunEvent.RunFailed.class);
    AgentRunEvent.RunFailed failed = (AgentRunEvent.RunFailed) last;

    assertThat(failed.message())
        .as("emitted message is one of the fixed safe strings")
        .isIn(SAFE_MESSAGES);
    assertThat(failed.message()).doesNotContain("sk-SECRET-123").doesNotContain("raw prompt");
    assertThat(failed.runId()).isEqualTo("run-2");
    assertThat(service.isActive("run-2")).isFalse();
  }

  @Test
  void serverCancelStopsProviderFlux() throws Exception {
    FakeStreamModel model =
        FakeStreamModel.builder()
            .text("a")
            .text("b")
            .text("c")
            .text("d")
            .finish(1, 4)
            .perFrameDelay(Duration.ofMillis(120))
            .build();
    AgentRunService service = newService();
    RunContext context = ctx("run-3");
    Flux<AgentRunEvent> flux =
        service.start(
            new RunRequest(
                context, new FakeModelAdapter(model), AgentRuntimeConfig.minimal("sys"), "hi"));

    Disposable subscription = flux.subscribe();
    service.bindSubscription("run-3", subscription);
    Thread.sleep(80); // allow streaming to begin

    boolean cancelled = service.cancel("run-3", "t1", "u1");

    assertThat(cancelled).isTrue();
    Thread.sleep(300); // allow cancel to propagate upstream to the provider flux
    assertThat(model.wasCancelled())
        .as("provider flux cancelled on server-initiated cancel")
        .isTrue();
    assertThat(service.isActive("run-3")).isFalse();
  }

  @Test
  void serverCancelRejectsNonOwner() throws Exception {
    FakeStreamModel model =
        FakeStreamModel.builder()
            .text("a")
            .finish(1, 1)
            .perFrameDelay(Duration.ofMillis(200))
            .build();
    AgentRunService service = newService();
    RunContext context = ctx("run-4");
    Disposable subscription =
        service
            .start(
                new RunRequest(
                    context, new FakeModelAdapter(model), AgentRuntimeConfig.minimal("sys"), "hi"))
            .subscribe();
    service.bindSubscription("run-4", subscription);

    assertThat(service.cancel("run-4", "t-other", "u1")).isFalse();
    assertThat(service.cancel("run-4", "t1", "u-other")).isFalse();
    assertThat(service.isActive("run-4")).as("run still active after rejected cancels").isTrue();

    subscription.dispose();
  }
}
