package io.datastoria.server.api.compat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.datastoria.server.TestDbHelper;
import io.datastoria.server.agent.application.ChatRunService;
import io.datastoria.server.agent.domain.AgentRun;
import io.datastoria.server.agent.domain.AgentRunEvent;
import io.datastoria.server.agent.domain.AgentRunStatus;
import io.datastoria.server.agent.testing.FakeModelAdapterProvider;
import io.datastoria.server.agent.testing.FakeStreamModel;
import io.datastoria.server.api.error.ResourceInUseException;
import io.datastoria.server.identity.Identity;
import io.datastoria.server.repository.AgentRunRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Integration test for {@link AiAgentController} ({@code POST /api/ai/agent}) with a fake model and
 * SQLite: SSE stream + fixed headers, client-secret rejection, idempotency, tenant/session/model
 * validation, provider-error sanitization, and run lifecycle persistence. No real provider.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "PT30S")
@ActiveProfiles("test")
@Import(AiAgentControllerTest.FakeModelConfig.class)
class AiAgentControllerTest {

  private static final String TENANT = "tenant-test";
  private static final String USER = "dev@example.com";
  private static final Instant NOW = Instant.parse("2026-07-25T09:00:00Z");

  @Autowired WebTestClient webTestClient;
  @Autowired ChatRunService chatRunService;
  @Autowired FakeModelAdapterProvider fakeProvider;
  @Autowired AgentRunRepository runRepository;
  @Autowired JdbcClient jdbc;
  @Autowired TestDbHelper dbHelper;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @TestConfiguration
  static class FakeModelConfig {
    @Bean
    @org.springframework.context.annotation.Primary
    FakeModelAdapterProvider modelAdapterProvider() {
      return new FakeModelAdapterProvider();
    }
  }

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
    seedProvider("prov-1");
    seedModel("mdl-1", "prov-1", "gpt-test");
    seedSession("sess-1");
    fakeProvider.reset();
  }

  @Test
  void happyTextStreamReturnsSseWithFixedHeaders() {
    String body = streamBody("sess-1", "mdl-1", "hello");

    String sse =
        webTestClient
            .post()
            .uri("/api/ai/agent")
            .header("x-datastoria-user-email", USER)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
            .expectHeader()
            .valueEquals("X-Vercel-AI-UI-Message-Stream", "v1")
            .expectHeader()
            .valueEquals("X-Accel-Buffering", "no")
            .expectHeader()
            .valueEquals("Cache-Control", "no-cache")
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    assertThat(sse).contains("\"type\":\"start\"").contains("\"messageId\":\"msg-1\"");
    assertThat(sse).contains("\"type\":\"text-delta\"").contains("Hello");
    assertThat(sse).contains("\"type\":\"finish\"").contains("\"inputTokens\":1");
    // Full SSE framing preserved end-to-end: frames are \n\n-separated and the stream ends with
    // [DONE].
    assertThat(sse).contains("\n\ndata: ");
    assertThat(sse).endsWith("data: [DONE]\n\n");
  }

  @Test
  void reasoningAndAccumulatedUsageStream() {
    fakeProvider.setModel(
        FakeStreamModel.builder().reasoning("thinking ").text("Answer").finish(4, 6).build());
    String body = streamBody("sess-1", "mdl-1", "go");

    String sse = postStream(body, null);

    assertThat(sse)
        .contains("\"type\":\"reasoning-start\"")
        .contains("\"type\":\"reasoning-delta\"");
    // usage accumulates (single model call here) and lands on finish.
    assertThat(sse)
        .contains("\"inputTokens\":4")
        .contains("\"outputTokens\":6")
        .contains("\"totalTokens\":10");
  }

  @Test
  void clientModelApiKeyRejectedBeforeAnyRun() {
    String body =
        "{\"sessionId\":\"sess-1\",\"connectionId\":\"ch-1\",\"message\":{\"id\":\"m\",\"role\":\"user\","
            + "\"parts\":[{\"type\":\"text\",\"text\":\"hi\"}]},"
            + "\"model\":{\"provider\":\"openai\",\"modelId\":\"gpt-test\",\"apiKey\":\"sk-leaked-123\"}}";
    webTestClient
        .post()
        .uri("/api/ai/agent")
        .header("x-datastoria-user-email", USER)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchange()
        .expectStatus()
        .isBadRequest();
    // No run was created for the rejected request.
    assertThat(runRepository.findBySession(TENANT, "sess-1")).isEmpty();
  }

  @Test
  void connectionPasswordRejected() {
    String body =
        "{\"sessionId\":\"sess-1\",\"connectionId\":\"ch-1\",\"message\":{\"id\":\"m\",\"role\":\"user\","
            + "\"parts\":[{\"type\":\"text\",\"text\":\"hi\"}]},\"modelConfigId\":\"mdl-1\","
            + "\"connection\":{\"user\":\"u\",\"password\":\"pw-leaked\"}}";
    webTestClient
        .post()
        .uri("/api/ai/agent")
        .header("x-datastoria-user-email", USER)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void missingSessionReturnsNotFound() {
    String body = streamBody("sess-missing", "mdl-1", "hi");
    webTestClient
        .post()
        .uri("/api/ai/agent")
        .header("x-datastoria-user-email", USER)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @Test
  void missingModelReturnsNotFound() {
    String body = streamBody("sess-1", "mdl-missing", "hi");
    webTestClient
        .post()
        .uri("/api/ai/agent")
        .header("x-datastoria-user-email", USER)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @Test
  void crossTenantSessionReturnsNotFound() {
    seedSessionOwned("sess-other", "tenant-other", "someone@example.com");
    String body = streamBody("sess-other", "mdl-1", "hi");
    // Acting as tenant-test user, the other-tenant session is invisible (404), never leaked.
    webTestClient
        .post()
        .uri("/api/ai/agent")
        .header("x-datastoria-user-email", USER)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @Test
  void providerErrorIsSanitizedInTheStream() {
    fakeProvider.setModel(
        FakeStreamModel.builder()
            .error(new RuntimeException("provider leak sk-SECRET-123 prompt echo"))
            .build());
    String body = streamBody("sess-1", "mdl-1", "go");

    String sse = postStream(body, "idem-err");

    assertThat(sse).contains("\"type\":\"error\"");
    assertThat(sse)
        .doesNotContain("sk-SECRET-123")
        .doesNotContain("provider leak")
        .doesNotContain("prompt echo");
  }

  @Test
  void serialIdempotencyKeyRejectsSecondRequest() {
    String body = streamBody("sess-1", "mdl-1", "hi");
    postStream(body, "idem-serial"); // first succeeds, run completes

    webTestClient
        .post()
        .uri("/api/ai/agent")
        .header("x-datastoria-user-email", USER)
        .header("Idempotency-Key", "idem-serial")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchange()
        .expectStatus()
        .isEqualTo(409); // RESOURCE_IN_USE — no second agent started
  }

  @Test
  void completedRunWithUsagePersistedAfterStream() {
    fakeProvider.setModel(FakeStreamModel.builder().text("Hi").finish(7, 9).build());
    postStream(streamBody("sess-1", "mdl-1", "hi"), "idem-persist");

    AgentRun run = awaitRun("idem-persist", AgentRunStatus.SUCCEEDED);
    assertThat(run).isNotNull();
    assertThat(run.status()).isEqualTo(AgentRunStatus.SUCCEEDED);
    assertThat(run.usageJson()).contains("\"inputTokens\":7").contains("\"totalTokens\":16");
    assertThat(run.errorCode()).isNull();
    assertThat(run.safeMessage()).isNull();
  }

  @Test
  void failedRunPersistedWithSafeCodeOnly() {
    fakeProvider.setModel(
        FakeStreamModel.builder().error(new IllegalStateException("rate limit raw sk-X")).build());
    postStream(streamBody("sess-1", "mdl-1", "hi"), "idem-fail");

    AgentRun run = awaitRun("idem-fail", AgentRunStatus.FAILED);
    assertThat(run).isNotNull();
    assertThat(run.status()).isEqualTo(AgentRunStatus.FAILED);
    assertThat(run.errorCode()).isEqualTo("MODEL_RATE_LIMITED");
    assertThat(run.safeMessage()).doesNotContain("sk-X").doesNotContain("rate limit raw");
  }

  @Test
  void concurrentSameIdempotencyKeyStartsExactlyOneRun() throws Exception {
    AgentChatRequest req = request("idem-concurrent");
    Identity identity = new Identity(TENANT, USER, Set.of("ROLE_USER"));
    AtomicInteger started = new AtomicInteger();
    AtomicInteger conflict = new AtomicInteger();
    CountDownLatch done = new CountDownLatch(2);
    java.util.function.Consumer<Mono<Flux<AgentRunEvent>>> fire =
        m ->
            m.subscribe(
                flux -> {
                  started.incrementAndGet();
                  flux.collectList().subscribe(ignored -> done.countDown(), e -> done.countDown());
                },
                err -> {
                  if (err instanceof ResourceInUseException) {
                    conflict.incrementAndGet();
                  }
                  done.countDown();
                });
    // Fire both on separate threads so the two prepareRun calls race on the jdbc scheduler.
    new Thread(() -> fire.accept(chatRunService.stream(req, identity))).start();
    new Thread(() -> fire.accept(chatRunService.stream(req, identity))).start();

    assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
    assertThat(started.get()).as("exactly one run started").isEqualTo(1);
    assertThat(conflict.get()).as("the loser was rejected as a duplicate").isEqualTo(1);
  }

  @Test
  void clientDisconnectCancelsRunAndPersistsCancelled() throws Exception {
    FakeStreamModel slow =
        FakeStreamModel.builder()
            .text("a")
            .text("b")
            .text("c")
            .text("d")
            .finish(1, 4)
            .perFrameDelay(Duration.ofMillis(120))
            .build();
    fakeProvider.setModel(slow);
    AgentChatRequest req = request("idem-disconnect");
    Identity identity = new Identity(TENANT, USER, Set.of("ROLE_USER"));

    Flux<AgentRunEvent> events = chatRunService.stream(req, identity).block(Duration.ofSeconds(10));
    assertThat(events).isNotNull();
    AtomicReference<Subscription> sub = new AtomicReference<>();
    CountDownLatch firstEvent = new CountDownLatch(1);
    events.subscribe(
        e -> firstEvent.countDown(),
        err -> {},
        () -> {},
        s -> {
          sub.set(s);
          s.request(Long.MAX_VALUE);
        });
    assertThat(firstEvent.await(5, TimeUnit.SECONDS)).as("stream started").isTrue();

    sub.get().cancel(); // simulate client disconnect

    Thread.sleep(400); // allow cancel to propagate upstream and to the persister
    assertThat(slow.wasCancelled()).as("provider flux cancelled (token emission stopped)").isTrue();
    AgentRun run = awaitRun("idem-disconnect", AgentRunStatus.CANCELLED);
    assertThat(run).as("run persisted as cancelled").isNotNull();
    assertThat(run.status()).isEqualTo(AgentRunStatus.CANCELLED);
  }

  // ---- helpers ----

  private AgentChatRequest request(String idempotencyKey) throws Exception {
    JsonNode message =
        MAPPER.readTree(
            "{\"id\":\"msg-1\",\"role\":\"user\",\"parts\":[{\"type\":\"text\",\"text\":\"hi\"}]}");
    return new AgentChatRequest(
        "sess-1", "ch-1", message, "mdl-1", null, null, idempotencyKey, false, true, false, null);
  }

  private String postStream(String body, String idempotencyKey) {
    var req =
        webTestClient
            .post()
            .uri("/api/ai/agent")
            .header("x-datastoria-user-email", USER)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body);
    if (idempotencyKey != null) {
      req.header("Idempotency-Key", idempotencyKey);
    }
    return req.exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .returnResult()
        .getResponseBody();
  }

  private AgentRun awaitRun(String idempotencyKey, AgentRunStatus expected) {
    long deadline = System.currentTimeMillis() + 4000;
    while (System.currentTimeMillis() < deadline) {
      Optional<AgentRun> r = runRepository.findByIdempotencyKey(TENANT, USER, idempotencyKey);
      if (r.isPresent() && r.get().status() == expected) {
        return r.get();
      }
      try {
        Thread.sleep(40);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    return runRepository.findByIdempotencyKey(TENANT, USER, idempotencyKey).orElse(null);
  }

  private static String streamBody(String sessionId, String modelConfigId, String text) {
    return "{\"sessionId\":\""
        + sessionId
        + "\",\"connectionId\":\"ch-1\",\"message\":{\"id\":\"msg-1\",\"role\":\"user\","
        + "\"parts\":[{\"type\":\"text\",\"text\":\""
        + text
        + "\"}]},\"modelConfigId\":\""
        + modelConfigId
        + "\"}";
  }

  private void seedProvider(String id) {
    jdbc.sql(
            "INSERT INTO ds_model_provider"
                + " (id, tenant_id, provider_key, display_name, auth_type, enabled, created_by,"
                + " updated_by, created_at, updated_at)"
                + " VALUES (:id,:t,'openai','OpenAI','api_key',1,'admin','admin',:now,:now)")
        .param("id", id)
        .param("t", TENANT)
        .param("now", NOW.toString())
        .update();
  }

  private void seedModel(String id, String providerId, String modelKey) {
    jdbc.sql(
            "INSERT INTO ds_model"
                + " (id, tenant_id, provider_id, model_key, display_name, source, enabled,"
                + " created_at, updated_at)"
                + " VALUES (:id,:t,:p,:k,'Test','system',1,:now,:now)")
        .param("id", id)
        .param("t", TENANT)
        .param("p", providerId)
        .param("k", modelKey)
        .param("now", NOW.toString())
        .update();
  }

  private void seedSession(String id) {
    seedSessionOwned(id, TENANT, USER);
  }

  private void seedSessionOwned(String id, String tenant, String user) {
    jdbc.sql(
            "INSERT INTO ds_chat_session"
                + " (id, tenant_id, user_id, connection_id, title, revision, created_at, updated_at)"
                + " VALUES (:id,:t,:u,'ch-1','t',0,:now,:now)")
        .param("id", id)
        .param("t", tenant)
        .param("u", user)
        .param("now", NOW.toString())
        .update();
  }
}
