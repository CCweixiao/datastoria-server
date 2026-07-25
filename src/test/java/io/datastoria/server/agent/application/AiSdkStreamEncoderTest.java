package io.datastoria.server.agent.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.datastoria.server.agent.domain.AgentRunEvent;
import io.datastoria.server.agent.domain.TokenUsage;

import reactor.core.publisher.Flux;

/**
 * Unit tests for {@link AiSdkStreamEncoder}: exact frames per scenario, AI SDK v6 usage shape, JSON
 * escaping, incremental per-event encoding (no buffering/lookahead), the {@code [DONE]} terminator,
 * and semantic type-sequence alignment with the golden fixtures in {@code docs/fixtures/stream}
 * (recording the deprecated promptTokens/inputTokens discrepancy).
 */
class AiSdkStreamEncoderTest {

  private static final Instant NOW = Instant.parse("2026-07-25T09:00:00Z");
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final AiSdkStreamEncoder encoder = new AiSdkStreamEncoder();

  // ---- scenario: text-only ----

  @Test
  void textOnlyScenarioProducesExpectedFrameSequence() {
    List<String> frames = encodeAll(textOnlyEvents());

    assertThat(types(frames))
        .containsExactly(
            "start",
            "start-step",
            "text-start",
            "text-delta",
            "text-delta",
            "text-end",
            "finish-step",
            "finish");
    assertThat(frames).last().isEqualTo("data: [DONE]\n\n");
    assertThat(messageIdOf(frames)).isEqualTo("msg_01H");
    assertThat(concatDeltas(frames, "text-delta")).isEqualTo("Hello world");
  }

  @Test
  void textOnlyTypeSequenceMatchesGoldenFixture() {
    assertThat(types(encodeAll(textOnlyEvents()))).isEqualTo(fixtureTypes("text-only.jsonl"));
  }

  // ---- scenario: reasoning ----

  @Test
  void reasoningScenarioProducesExpectedFrameSequence() {
    List<String> frames = encodeAll(reasoningEvents());
    assertThat(types(frames))
        .containsExactly(
            "start",
            "start-step",
            "reasoning-start",
            "reasoning-delta",
            "reasoning-end",
            "text-start",
            "text-delta",
            "text-end",
            "finish-step",
            "finish");
    assertThat(types(frames)).isEqualTo(fixtureTypes("reasoning.jsonl"));
  }

  @Test
  void reasoningPartIdIsSharedWithinBlock() {
    List<JsonNode> chunks = chunks(encodeAll(reasoningEvents()));
    String startId = id(chunks, "reasoning-start");
    String endId = id(chunks, "reasoning-end");
    assertThat(startId).isNotNull().isEqualTo(endId);
    assertThat(
            chunks.stream()
                .filter(c -> "reasoning-delta".equals(type(c)))
                .map(c -> c.get("id").asText()))
        .allMatch(startId::equals);
  }

  // ---- scenario: usage ----

  @Test
  void usageEmittedOnFinishInLanguageModelUsageShape() {
    List<JsonNode> chunks = chunks(encodeAll(textOnlyEvents()));
    JsonNode usage =
        chunks.stream()
            .filter(c -> "finish".equals(type(c)))
            .map(c -> c.get("messageMetadata").get("usage"))
            .findFirst()
            .orElseThrow();
    // Matches the live Node A01 output (normalizeUsage/sumTokenUsage), NOT the deprecated
    // promptTokens/completionTokens naming used in the hand-built fixture.
    assertThat(usage.get("inputTokens").asInt()).isEqualTo(12);
    assertThat(usage.get("outputTokens").asInt()).isEqualTo(3);
    assertThat(usage.get("totalTokens").asInt()).isEqualTo(15);
    assertThat(usage.get("inputTokenDetails").get("cacheReadTokens").asInt()).isZero();
    assertThat(usage.get("outputTokenDetails").get("reasoningTokens").asInt()).isZero();
  }

  @Test
  void usageIsAccumulatedAcrossModelCalls() {
    List<String> frames =
        encodeAll(
            List.of(
                started(),
                new AgentRunEvent.UsageReported("run_1", 2, NOW, new TokenUsage(10, 2, 3, 0d)),
                new AgentRunEvent.UsageReported("run_1", 3, NOW, new TokenUsage(7, 5, 1, 0d)),
                new AgentRunEvent.RunCompleted("run_1", 4, NOW)));
    JsonNode usage =
        chunks(frames).stream()
            .filter(c -> "finish".equals(type(c)))
            .map(c -> c.path("messageMetadata").path("usage"))
            .findFirst()
            .orElseThrow();
    assertThat(usage.path("inputTokens").asLong()).isEqualTo(17);
    assertThat(usage.path("outputTokens").asLong()).isEqualTo(7);
    assertThat(usage.path("inputTokenDetails").path("cacheReadTokens").asLong()).isEqualTo(4);
    assertThat(usage.path("inputTokenDetails").path("noCacheTokens").asLong()).isEqualTo(13);
    assertThat(usage.path("totalTokens").asLong()).isEqualTo(24);
  }

  @Test
  void oneHundredFreshStreamsRemainByteStable() {
    List<String> expected = encodeAll(textOnlyEvents());
    for (int run = 0; run < 100; run++) {
      assertThat(encodeAll(textOnlyEvents())).isEqualTo(expected);
    }
  }

  @Test
  void goldenFixtureUsesDeprecatedUsageNaming() {
    // Documents the discrepancy: the fixture freezes promptTokens/completionTokens, but the live
    // frontend (route.ts -> sumTokenUsage) emits inputTokens/outputTokens. Encoder follows the live
    // behavior. See P4.5 report.
    JsonNode fixtureUsage =
        fixtureChunks("text-only.jsonl").stream()
            .filter(c -> "finish".equals(type(c)))
            .map(c -> c.get("messageMetadata").get("usage"))
            .findFirst()
            .orElseThrow();
    assertThat(fixtureUsage.has("promptTokens")).isTrue();
    assertThat(fixtureUsage.has("inputTokens")).isFalse();
  }

  // ---- scenario: error ----

  @Test
  void errorScenarioEmitsSafeErrorTextAndNoFinish() {
    List<String> all = encodeAll(errorEvents());
    assertThat(types(all)).containsExactly("start", "start-step", "error");
    assertThat(types(all)).isEqualTo(fixtureTypes("error.jsonl"));
    JsonNode errorChunk =
        chunks(all).stream().filter(c -> "error".equals(type(c))).findFirst().orElseThrow();
    assertThat(errorChunk.get("errorText").asText())
        .isEqualTo("The model is busy. Please retry shortly.");
    // No raw provider stack trace / no prompt leak.
    assertThat(errorChunk.get("errorText").asText()).doesNotContain("sk-").doesNotContain("apiKey");
  }

  @Test
  void errorMessageIsDerivedFromCodeAndNeverTrustsEventText() {
    AgentRunEvent.RunFailed malicious =
        new AgentRunEvent.RunFailed(
            "run_1",
            2,
            NOW,
            "UNKNOWN_PROVIDER_CODE",
            "raw provider failure sk-SECRET prompt fragment");
    JsonNode error =
        chunks(encodeAll(List.of(started(), malicious))).stream()
            .filter(c -> "error".equals(type(c)))
            .findFirst()
            .orElseThrow();
    assertThat(error.path("errorText").asText())
        .isEqualTo("The agent run failed. Please retry.")
        .doesNotContain("sk-SECRET", "prompt fragment");
  }

  // ---- scenario: cancel ----

  @Test
  void cancelScenarioEmitsAbortWithClientDisconnectReason() {
    List<String> frames = encodeAll(cancelEvents());
    assertThat(types(frames))
        .containsExactly("start", "start-step", "text-start", "text-delta", "abort");
    assertThat(types(frames)).isEqualTo(fixtureTypes("cancel.jsonl"));
    JsonNode abortChunk =
        chunks(frames).stream().filter(c -> "abort".equals(type(c))).findFirst().orElseThrow();
    assertThat(abortChunk.get("reason").asText()).isEqualTo("client_disconnect");
  }

  @Test
  void toolAndApprovalEventsUseAiSdkV6WireContract() {
    List<String> frames =
        encodeAll(
            List.of(
                started(),
                new AgentRunEvent.ToolInputStarted("run_1", 2, NOW, "call-1", "execute_sql"),
                new AgentRunEvent.ToolInputDelta(
                    "run_1", 3, NOW, "call-1", "execute_sql", "{\"sql\":\"SELECT 1\"}"),
                new AgentRunEvent.ToolInputAvailable(
                    "run_1", 4, NOW, "call-1", "execute_sql", "{\"sql\":\"SELECT 1\"}"),
                new AgentRunEvent.ToolOutputStarted("run_1", 5, NOW, "call-1", "execute_sql"),
                new AgentRunEvent.ToolOutputDelta(
                    "run_1", 6, NOW, "call-1", "execute_sql", "{\"rows\":[]}"),
                new AgentRunEvent.ToolOutputAvailable(
                    "run_1", 7, NOW, "call-1", "execute_sql", "{\"rows\":[]}", false, false),
                new AgentRunEvent.ToolApprovalRequired(
                    "run_1",
                    8,
                    NOW,
                    "reply-1",
                    List.of(
                        new AgentRunEvent.ToolApproval(
                            "act-1", "call-2", "execute_sql", "{\"sql\":\"SELECT 2\"}")))));

    assertThat(types(frames))
        .containsExactly(
            "start",
            "start-step",
            "tool-input-start",
            "tool-input-delta",
            "tool-input-available",
            "tool-output-available",
            "tool-approval-request");
    List<JsonNode> chunks = chunks(frames);
    assertThat(
            chunks.stream()
                .filter(c -> "tool-input-available".equals(type(c)))
                .findFirst()
                .orElseThrow()
                .path("input")
                .path("sql")
                .asText())
        .isEqualTo("SELECT 1");
    JsonNode approval =
        chunks.stream()
            .filter(c -> "tool-approval-request".equals(type(c)))
            .findFirst()
            .orElseThrow();
    assertThat(approval.path("approvalId").asText()).isEqualTo("act-1");
    assertThat(approval.path("toolCallId").asText()).isEqualTo("call-2");
  }

  @Test
  void deniedAndFailedToolsNeverExposeRawOutput() {
    List<JsonNode> chunks =
        chunks(
            encodeAll(
                List.of(
                    new AgentRunEvent.ToolOutputAvailable(
                        "r", 1, NOW, "denied", "danger", "sk-secret", false, true),
                    new AgentRunEvent.ToolOutputAvailable(
                        "r", 2, NOW, "failed", "query", "password=secret", true, false))));
    assertThat(chunks)
        .extracting(AiSdkStreamEncoderTest::type)
        .containsExactly("tool-output-denied", "tool-output-error");
    assertThat(chunks.toString()).doesNotContain("sk-secret", "password=secret");
  }

  // ---- JSON escaping + fragment boundaries ----

  @Test
  void jsonSpecialCharactersAreEscaped() {
    String tricky = "quote\" backslash\\ newline\n tab\t ctrl end";
    List<JsonNode> chunks =
        chunks(
            encodeAll(
                List.of(
                    started(),
                    new AgentRunEvent.TextBlockStarted("r", 2, NOW),
                    new AgentRunEvent.TextDelta("r", 3, NOW, tricky),
                    new AgentRunEvent.TextBlockEnded("r", 4, NOW))));
    JsonNode delta =
        chunks.stream().filter(c -> "text-delta".equals(type(c))).findFirst().orElseThrow();
    // Round-trips exactly through the escaped JSON wire form.
    assertThat(delta.get("delta").asText()).isEqualTo(tricky);
  }

  @Test
  void injectedPrettyPrintingMapperCannotBreakSingleLineSseFrames() {
    ObjectMapper pretty = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    AiSdkStreamEncoder compactEncoder = new AiSdkStreamEncoder(pretty);
    List<String> frames = compactEncoder.encode(started());
    assertThat(frames).allMatch(frame -> frame.indexOf('\n') == frame.length() - 2);
    assertThat(pretty.isEnabled(SerializationFeature.INDENT_OUTPUT))
        .as("encoder must not mutate the shared mapper")
        .isTrue();
  }

  @Test
  void eachTextDeltaIsItsOwnFrameNoBufferingOrLookahead() {
    // Incremental: encode() per event returns only that event's frames.
    assertThat(encoder.encode(started()))
        .containsExactly(
            "data: {\"type\":\"start\",\"messageId\":\"msg_01H\"}\n\n",
            "data: {\"type\":\"start-step\"}\n\n");
    assertThat(encoder.encode(new AgentRunEvent.TextBlockStarted("r", 2, NOW))).hasSize(1);
    assertThat(encoder.encode(new AgentRunEvent.TextDelta("r", 3, NOW, "Hel")))
        .as("a delta emits exactly one frame, no lookahead/merging")
        .hasSize(1);
    assertThat(encoder.encode(new AgentRunEvent.TextDelta("r", 4, NOW, "lo"))).hasSize(1);
    // Usage is buffered (emits nothing now) but surfaces on finish.
    assertThat(
            encoder.encode(
                new AgentRunEvent.UsageReported("r", 5, NOW, new TokenUsage(1, 1, 0, 0d))))
        .isEmpty();
    List<String> finishFrames = encoder.encode(new AgentRunEvent.RunCompleted("r", 6, NOW));
    assertThat(finishFrames).hasSize(2);
    assertThat(finishFrames.get(0)).contains("\"type\":\"finish-step\"");
    assertThat(finishFrames.get(1)).contains("\"inputTokens\":1").contains("\"totalTokens\":2");
  }

  // ---- terminator + reactive wiring ----

  @Test
  void doneIsTerminalMarker() {
    assertThat(encoder.done()).isEqualTo("data: [DONE]\n\n");
  }

  @Test
  void encodeFluxIsIncrementalAndTerminatesWithDone() {
    List<String> frames =
        AiSdkStreamEncoder.encode(Flux.fromIterable(textOnlyEvents())).collectList().block();
    assertThat(frames).isNotNull();
    assertThat(types(frames))
        .containsExactly(
            "start",
            "start-step",
            "text-start",
            "text-delta",
            "text-delta",
            "text-end",
            "finish-step",
            "finish");
    assertThat(frames).last().isEqualTo("data: [DONE]\n\n");
  }

  // ---- event fixtures ----

  private static AgentRunEvent.RunStarted started() {
    return new AgentRunEvent.RunStarted("run_1", 1, NOW, "sess_1", "msg_01H");
  }

  private static List<AgentRunEvent> textOnlyEvents() {
    return List.of(
        started(),
        new AgentRunEvent.TextBlockStarted("run_1", 2, NOW),
        new AgentRunEvent.TextDelta("run_1", 3, NOW, "Hello"),
        new AgentRunEvent.TextDelta("run_1", 4, NOW, " world"),
        new AgentRunEvent.TextBlockEnded("run_1", 5, NOW),
        new AgentRunEvent.UsageReported("run_1", 6, NOW, new TokenUsage(12, 3, 0, 0d)),
        new AgentRunEvent.RunCompleted("run_1", 7, NOW));
  }

  private static List<AgentRunEvent> reasoningEvents() {
    return List.of(
        started(),
        new AgentRunEvent.ReasoningBlockStarted("run_1", 2, NOW),
        new AgentRunEvent.ReasoningDelta("run_1", 3, NOW, "Thinking about schema"),
        new AgentRunEvent.ReasoningBlockEnded("run_1", 4, NOW),
        new AgentRunEvent.TextBlockStarted("run_1", 5, NOW),
        new AgentRunEvent.TextDelta("run_1", 6, NOW, "Use get_tables"),
        new AgentRunEvent.TextBlockEnded("run_1", 7, NOW),
        new AgentRunEvent.UsageReported("run_1", 8, NOW, new TokenUsage(20, 8, 0, 0d)),
        new AgentRunEvent.RunCompleted("run_1", 9, NOW));
  }

  private static List<AgentRunEvent> errorEvents() {
    return List.of(
        started(),
        new AgentRunEvent.RunFailed(
            "run_1", 2, NOW, "MODEL_RATE_LIMITED", "Model rate limit exceeded"));
  }

  private static List<AgentRunEvent> cancelEvents() {
    return List.of(
        started(),
        new AgentRunEvent.TextBlockStarted("run_1", 2, NOW),
        new AgentRunEvent.TextDelta("run_1", 3, NOW, "Generat"),
        new AgentRunEvent.RunCancelled("run_1", 4, NOW));
  }

  private List<String> encodeAll(List<AgentRunEvent> events) {
    AiSdkStreamEncoder enc = new AiSdkStreamEncoder();
    List<String> all = new ArrayList<>();
    events.forEach(e -> all.addAll(enc.encode(e)));
    all.add(enc.done());
    return all;
  }

  // ---- frame parsing helpers ----

  private static List<String> types(List<String> frames) {
    return chunks(frames).stream().map(AiSdkStreamEncoderTest::type).toList();
  }

  private static List<JsonNode> chunks(List<String> frames) {
    List<JsonNode> out = new ArrayList<>();
    for (String f : frames) {
      if (!f.startsWith("data: ") || !f.endsWith("\n\n")) {
        throw new AssertionError("malformed frame: " + f);
      }
      String payload = f.substring("data: ".length(), f.length() - 2);
      if (payload.equals("[DONE]")) {
        continue;
      }
      out.add(parse(payload));
    }
    return out;
  }

  private static String type(JsonNode c) {
    return c.get("type").asText();
  }

  private static String id(List<JsonNode> chunks, String type) {
    return chunks.stream()
        .filter(c -> type.equals(type(c)))
        .map(c -> c.get("id"))
        .filter(Objects::nonNull)
        .map(JsonNode::asText)
        .findFirst()
        .orElse(null);
  }

  private static String messageIdOf(List<String> frames) {
    return chunks(frames).stream()
        .filter(c -> "start".equals(type(c)))
        .map(c -> c.get("messageId").asText())
        .findFirst()
        .orElseThrow();
  }

  private static String concatDeltas(List<String> frames, String type) {
    return chunks(frames).stream()
        .filter(c -> type.equals(type(c)))
        .map(c -> c.get("delta").asText())
        .reduce("", String::concat);
  }

  private static JsonNode parse(String json) {
    try {
      return MAPPER.readTree(json);
    } catch (Exception e) {
      throw new AssertionError("invalid JSON: " + json, e);
    }
  }

  // ---- golden fixture helpers ----

  private static List<String> fixtureTypes(String file) {
    return fixtureChunks(file).stream().map(AiSdkStreamEncoderTest::type).toList();
  }

  private static List<JsonNode> fixtureChunks(String file) {
    try {
      List<String> lines = Files.readAllLines(Path.of("docs/fixtures/stream/" + file));
      List<JsonNode> out = new ArrayList<>();
      for (String line : lines) {
        if (line.isBlank()) {
          continue;
        }
        out.add(parse(line));
      }
      return out;
    } catch (Exception e) {
      // Fixture files live under docs/; if the CWD is not the module root, skip with an assumption.
      org.junit.jupiter.api.Assumptions.assumeTrue(false, "fixture not readable: " + file);
      return Stream.<JsonNode>empty().toList();
    }
  }
}
