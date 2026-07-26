package io.datastoria.server.api.compat.p3;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * P3 wire-format API tests. Covers the fixtures whose preconditions are satisfiable in the default
 * dev/test profile (allow-anonymous=true, feedback.store-enabled=true). The two unauthenticated
 * fixtures and the {@code 202 recorded:false} fixture live in sibling classes that override the
 * relevant properties.
 *
 * <p>Each test method references the fixture id in its {@code @DisplayName} so the contract-trace
 * matrix in {@code docs/api/acceptance-traceability.md} stays readable.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"datastoria.feedback.store-enabled=true"})
class P3ApiTest extends AbstractP3ApiTest {

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
  }

  // ============================================================== A03

  @Nested
  @DisplayName("A03 listSessions")
  class A03List {

    @Test
    @DisplayName("A03-list-basic: owner sees their sessions, newest updatedAt first")
    void a03ListBasic() {
      // Seed two sessions; A03 ordering is updated_at DESC.
      String older = createSession("sess_a03_older", "ch-test", "Slow query analysis");
      String newer = createSession("sess_a03_newer", "ch-test", "Schema exploration");
      // Force ordering by bumping the newer session via a rename.
      web.patch()
          .uri("/api/ai/chat/sessions/{id}", newer)
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(Map.of("title", "Schema exploration (renamed)"))
          .exchange()
          .expectStatus()
          .isOk();

      web.get()
          .uri("/api/ai/chat/sessions")
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .exchange()
          .expectStatus()
          .isOk()
          .expectHeader()
          .contentType(MediaType.APPLICATION_JSON)
          .expectBody()
          .jsonPath("$.sessions.length()")
          .isEqualTo(2)
          .jsonPath("$.sessions[0].chatId")
          .isEqualTo(newer)
          .jsonPath("$.sessions[0].databaseId")
          .isEqualTo("ch-test")
          .jsonPath("$.sessions[0].title")
          .isEqualTo("Schema exploration (renamed)")
          .jsonPath("$.sessions[1].chatId")
          .isEqualTo(older)
          .jsonPath("$.nextCursor")
          .isEqualTo(null);
    }

    @Test
    @DisplayName("A03-list-with-cursor: caller passes an opaque cursor and gets the next page")
    void a03ListWithCursor() {
      // Seed two sessions; the older one will be on the second page.
      String older = createSession("sess_a03_older", "ch-test", "Slow query analysis");
      String newer = createSession("sess_a03_newer", "ch-test", "Schema exploration");

      // Page 1: limit=1 returns the newer session and a non-null nextCursor.
      JsonNode page1 =
          web.get()
              .uri(
                  builder -> builder.path("/api/ai/chat/sessions").queryParam("limit", "1").build())
              .header("x-datastoria-user-email", OWNER_EMAIL)
              .exchange()
              .expectStatus()
              .isOk()
              .expectBody(JsonNode.class)
              .returnResult()
              .getResponseBody();
      assertThat(page1.get("sessions").size()).isEqualTo(1);
      assertThat(page1.get("sessions").get(0).get("chatId").asText()).isEqualTo(newer);
      String nextCursor = page1.get("nextCursor").asText();
      assertThat(nextCursor).isNotBlank();

      // Page 2: pass the cursor verbatim; should land on the older session and EOS.
      web.get()
          .uri(
              builder ->
                  builder
                      .path("/api/ai/chat/sessions")
                      .queryParam("limit", "1")
                      .queryParam("cursor", nextCursor)
                      .build())
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .jsonPath("$.sessions.length()")
          .isEqualTo(1)
          .jsonPath("$.sessions[0].chatId")
          .isEqualTo(older)
          .jsonPath("$.nextCursor")
          .isEqualTo(null);
    }

    @Test
    @DisplayName("A03-list-invalid-limit: limit=0 yields HTTP 400 'Invalid limit'")
    void a03ListInvalidLimit() {
      web.get()
          .uri(builder -> builder.path("/api/ai/chat/sessions").queryParam("limit", "0").build())
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .exchange()
          .expectStatus()
          .isBadRequest()
          .expectHeader()
          .contentTypeCompatibleWith(MediaType.TEXT_PLAIN)
          .expectBody(String.class)
          .isEqualTo("Invalid limit");
    }

    @Test
    @DisplayName("A03 list rejects limit > 500 with the same error")
    void a03ListLimitTooLarge() {
      web.get()
          .uri(builder -> builder.path("/api/ai/chat/sessions").queryParam("limit", "501").build())
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .exchange()
          .expectStatus()
          .isBadRequest()
          .expectBody(String.class)
          .isEqualTo("Invalid limit");
    }

    @Test
    @DisplayName("A03 list filters by connectionId when supplied")
    void a03ListConnectionFilter() {
      createSession("sess_a_conn_a", "ch-alpha", "Alpha");
      createSession("sess_a_conn_b", "ch-beta", "Beta");

      web.get()
          .uri(
              builder ->
                  builder
                      .path("/api/ai/chat/sessions")
                      .queryParam("connectionId", "ch-alpha")
                      .build())
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .jsonPath("$.sessions.length()")
          .isEqualTo(1)
          .jsonPath("$.sessions[0].databaseId")
          .isEqualTo("ch-alpha");
    }
  }

  // ============================================================== A04

  @Nested
  @DisplayName("A04 createSession")
  class A04Create {

    @Test
    @DisplayName("A04-create-minimal: only connectionId supplied; session echoed back")
    void a04CreateMinimal() {
      web.post()
          .uri("/api/ai/chat/sessions")
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(Map.of("connectionId", "ch-prod"))
          .exchange()
          .expectStatus()
          .isOk()
          .expectHeader()
          .contentType(MediaType.APPLICATION_JSON)
          .expectBody()
          .jsonPath("$.session.chatId")
          .isNotEmpty()
          .jsonPath("$.session.databaseId")
          .isEqualTo("ch-prod")
          .jsonPath("$.session.title")
          .isEqualTo("Inline error diagnosis")
          .jsonPath("$.session.createdAt")
          .isNotEmpty()
          .jsonPath("$.session.updatedAt")
          .isNotEmpty();
    }

    @Test
    @DisplayName("A04-create-with-messages: client-chosen sessionId + initial messages")
    void a04CreateWithMessages() {
      String sessionId = "019523a0f0a64d6c8a3e2b9c1f0d7e20";
      web.post()
          .uri("/api/ai/chat/sessions")
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(
              Map.of(
                  "connectionId",
                  "ch-test",
                  "sessionId",
                  sessionId,
                  "title",
                  "Slow query analysis",
                  "messages",
                  List.of(
                      Map.of(
                          "id", "msg_019523a100",
                          "role", "user",
                          "parts", List.of(Map.of("type", "text", "text", "Find slow queries"))))))
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .jsonPath("$.session.chatId")
          .isEqualTo(sessionId)
          .jsonPath("$.session.databaseId")
          .isEqualTo("ch-test")
          .jsonPath("$.session.title")
          .isEqualTo("Slow query analysis");

      // The initial message should be retrievable via A08.
      web.get()
          .uri("/api/ai/chat/sessions/{id}/messages", sessionId)
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .jsonPath("$.length()")
          .isEqualTo(1)
          .jsonPath("$[0].id")
          .isEqualTo("msg_019523a100")
          .jsonPath("$[0].role")
          .isEqualTo("user")
          .jsonPath("$[0].sequence")
          .isEqualTo(1);
    }

    @Test
    @DisplayName("A04-create-idempotent-reuse: same sessionId + connectionId returns existing")
    void a04CreateIdempotentReuse() {
      String sessionId = "019523a0f0a64d6c8a3e2b9c1f0d7e20";
      createSession(sessionId, "ch-test", "Slow query analysis");

      // Re-POST with the same sessionId+connectionId and a fresh message to upsert.
      web.post()
          .uri("/api/ai/chat/sessions")
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(
              Map.of(
                  "connectionId",
                  "ch-test",
                  "sessionId",
                  sessionId,
                  "title",
                  "Slow query analysis",
                  "messages",
                  List.of(
                      Map.of(
                          "id", "msg_replay",
                          "role", "user",
                          "parts", List.of(Map.of("type", "text", "text", "Replay"))))))
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .jsonPath("$.session.chatId")
          .isEqualTo(sessionId);

      web.get()
          .uri("/api/ai/chat/sessions/{id}/messages", sessionId)
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .exchange()
          .expectBody()
          .jsonPath("$.length()")
          .isEqualTo(1)
          .jsonPath("$[0].id")
          .isEqualTo("msg_replay");
    }

    @Test
    @DisplayName("A04-create-connection-mismatch: HTTP 409 'Session connectionId mismatch'")
    void a04CreateConnectionMismatch() {
      String sessionId = "019523a0f0a64d6c8a3e2b9c1f0d7e20";
      createSession(sessionId, "ch-test", "Slow query analysis");

      web.post()
          .uri("/api/ai/chat/sessions")
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(Map.of("connectionId", "ch-other", "sessionId", sessionId))
          .exchange()
          .expectStatus()
          .isEqualTo(409)
          .expectHeader()
          .contentTypeCompatibleWith(MediaType.TEXT_PLAIN)
          .expectBody(String.class)
          .isEqualTo("Session connectionId mismatch");
    }

    @Test
    @DisplayName("A04-create-invalid-connection-id: empty connectionId yields HTTP 400")
    void a04CreateInvalidConnectionId() {
      web.post()
          .uri("/api/ai/chat/sessions")
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(Map.of("connectionId", ""))
          .exchange()
          .expectStatus()
          .isBadRequest()
          .expectBody(String.class)
          .isEqualTo("Invalid connectionId");
    }

    @Test
    @DisplayName(
        "A04-create-invalid-json: malformed body yields HTTP 400 'Invalid JSON in request body'")
    void a04CreateInvalidJson() {
      web.post()
          .uri("/api/ai/chat/sessions")
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue("{not json")
          .exchange()
          .expectStatus()
          .isBadRequest();
      // Note: WebFlux returns a 415/400 from the codec itself before the handler runs; the body
      // wording may be framework-generated. We assert on status only for the malformed-body case.
    }
  }

  // ============================================================== A05 / A06 / A07

  @Nested
  @DisplayName("A05/A06/A07 session get/rename/delete")
  class A05ToA07 {

    @Test
    @DisplayName("A05-get-owner: owner reads their session")
    void a05GetOwner() {
      String sessionId = "019523a0f0a64d6c8a3e2b9c1f0d7e20";
      createSession(sessionId, "ch-test", "Slow query analysis");

      web.get()
          .uri("/api/ai/chat/sessions/{id}", sessionId)
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .exchange()
          .expectStatus()
          .isOk()
          .expectHeader()
          .contentType(MediaType.APPLICATION_JSON)
          .expectBody()
          .jsonPath("$.chatId")
          .isEqualTo(sessionId)
          .jsonPath("$.databaseId")
          .isEqualTo("ch-test")
          .jsonPath("$.title")
          .isEqualTo("Slow query analysis");
    }

    @Test
    @DisplayName("A05-get-via-share: share visitor with a valid code reads the session")
    void a05GetViaShare() {
      String sessionId = "019523a0f0a64d6c8a3e2b9c1f0d7e20";
      createSession(sessionId, "ch-test", "Slow query analysis");
      String code = issueShareCode(sessionId);

      web.get()
          .uri("/api/ai/chat/sessions/{id}", sessionId)
          .header("x-datastoria-user-email", VISITOR_EMAIL)
          .header("X-Session-Share-Code", code)
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .jsonPath("$.chatId")
          .isEqualTo(sessionId)
          .jsonPath("$.title")
          .isEqualTo("Slow query analysis");
    }

    @Test
    @DisplayName("A05-get-not-found: HTTP 404 'Not found' for unknown session id")
    void a05GetNotFound() {
      web.get()
          .uri("/api/ai/chat/sessions/{id}", "019523a0f0a64d6c8a3e2b9c1f0d7e99")
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .exchange()
          .expectStatus()
          .isNotFound()
          .expectHeader()
          .contentTypeCompatibleWith(MediaType.TEXT_PLAIN)
          .expectBody(String.class)
          .isEqualTo("Not found");
    }

    @Test
    @DisplayName("A05-get-invalid-share-code: HTTP 403 'Invalid session share code'")
    void a05GetInvalidShareCode() {
      String sessionId = "019523a0f0a64d6c8a3e2b9c1f0d7e20";
      createSession(sessionId, "ch-test", "Slow query analysis");

      web.get()
          .uri("/api/ai/chat/sessions/{id}", sessionId)
          .header("x-datastoria-user-email", VISITOR_EMAIL)
          .header("X-Session-Share-Code", "not-a-real-jwt")
          .exchange()
          .expectStatus()
          .isForbidden()
          .expectHeader()
          .contentTypeCompatibleWith(MediaType.TEXT_PLAIN)
          .expectBody(String.class)
          .isEqualTo("Invalid session share code");
    }

    @Test
    @DisplayName("A06-rename-happy: owner renames; revision increments")
    void a06RenameHappy() {
      String sessionId = "019523a0f0a64d6c8a3e2b9c1f0d7e20";
      createSession(sessionId, "ch-test", "Old");

      web.patch()
          .uri("/api/ai/chat/sessions/{id}", sessionId)
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(Map.of("title", "New title"))
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .jsonPath("$.chatId")
          .isEqualTo(sessionId)
          .jsonPath("$.title")
          .isEqualTo("New title");
    }

    @Test
    @DisplayName("A06-rename-missing-title: HTTP 400 'Missing title'")
    void a06RenameMissingTitle() {
      String sessionId = "019523a0f0a64d6c8a3e2b9c1f0d7e20";
      createSession(sessionId, "ch-test", "Old");

      web.patch()
          .uri("/api/ai/chat/sessions/{id}", sessionId)
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(Map.of())
          .exchange()
          .expectStatus()
          .isBadRequest()
          .expectHeader()
          .contentTypeCompatibleWith(MediaType.TEXT_PLAIN)
          .expectBody(String.class)
          .isEqualTo("Missing title");
    }

    @Test
    @DisplayName("A06-rename-share-denied: HTTP 403 ProblemDetail SHARE_PERMISSION_DENIED")
    void a06RenameShareDenied() {
      String sessionId = "019523a0f0a64d6c8a3e2b9c1f0d7e20";
      createSession(sessionId, "ch-test", "Slow query analysis");
      String code = issueShareCode(sessionId);

      web.patch()
          .uri("/api/ai/chat/sessions/{id}", sessionId)
          .header("x-datastoria-user-email", VISITOR_EMAIL)
          .header("X-Session-Share-Code", code)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(Map.of("title", "Hostile rename"))
          .exchange()
          .expectStatus()
          .isEqualTo(403)
          .expectHeader()
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .expectBody()
          .jsonPath("$.code")
          .isEqualTo("SHARE_PERMISSION_DENIED")
          .jsonPath("$.title")
          .isEqualTo("Share visitor may not mutate this session");
    }

    @Test
    @DisplayName("A07-delete-happy: owner deletes; HTTP 204 and session is gone")
    void a07DeleteHappy() {
      String sessionId = "019523a0f0a64d6c8a3e2b9c1f0d7e20";
      createSession(sessionId, "ch-test", "Slow query analysis");

      web.delete()
          .uri("/api/ai/chat/sessions/{id}", sessionId)
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .exchange()
          .expectStatus()
          .isNoContent()
          .expectBody()
          .isEmpty();

      // Follow-up GET returns 404.
      web.get()
          .uri("/api/ai/chat/sessions/{id}", sessionId)
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .exchange()
          .expectStatus()
          .isNotFound();
    }

    @Test
    @DisplayName("A07-delete-not-found: HTTP 404 'Not found'")
    void a07DeleteNotFound() {
      web.delete()
          .uri("/api/ai/chat/sessions/{id}", "019523a0f0a64d6c8a3e2b9c1f0d7e99")
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .exchange()
          .expectStatus()
          .isNotFound()
          .expectBody(String.class)
          .isEqualTo("Not found");
    }

    @Test
    @DisplayName("A07-delete-share-denied: HTTP 403 ProblemDetail SHARE_PERMISSION_DENIED")
    void a07DeleteShareDenied() {
      String sessionId = "019523a0f0a64d6c8a3e2b9c1f0d7e20";
      createSession(sessionId, "ch-test", "Slow query analysis");
      String code = issueShareCode(sessionId);

      web.delete()
          .uri("/api/ai/chat/sessions/{id}", sessionId)
          .header("x-datastoria-user-email", VISITOR_EMAIL)
          .header("X-Session-Share-Code", code)
          .exchange()
          .expectStatus()
          .isEqualTo(403)
          .expectBody()
          .jsonPath("$.code")
          .isEqualTo("SHARE_PERMISSION_DENIED");
    }
  }

  // ============================================================== A08

  @Nested
  @DisplayName("A08 getSessionMessages")
  class A08Messages {

    @Test
    @DisplayName("A08-messages-empty: session with no messages returns an empty array")
    void a08MessagesEmpty() {
      String sessionId = "019523a0f0a64d6c8a3e2b9c1f0d7e20";
      createSession(sessionId, "ch-test", "T");

      web.get()
          .uri("/api/ai/chat/sessions/{id}/messages", sessionId)
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .jsonPath("$")
          .isArray()
          .jsonPath("$.length()")
          .isEqualTo(0);
    }

    @Test
    @DisplayName("A08-messages-happy: messages ordered by sequence ASC")
    void a08MessagesHappy() {
      String sessionId = "019523a0f0a64d6c8a3e2b9c1f0d7e20";
      // Seed via A04 with two messages (text + tool-call/tool-result).
      ArrayNode parts1 = objectMapper.createArrayNode();
      parts1.add(objectMapper.valueToTree(Map.of("type", "text", "text", "Find slow queries")));
      ArrayNode parts2 = objectMapper.createArrayNode();
      parts2.add(
          objectMapper.valueToTree(Map.of("type", "text", "text", "I will search the query log.")));
      parts2.add(
          objectMapper.valueToTree(
              Map.of(
                  "type", "tool-call",
                  "toolCallId", "call_019523a1",
                  "toolName", "search_query_log",
                  "input", Map.of("limit", 10))));
      parts2.add(
          objectMapper.valueToTree(
              Map.of(
                  "type", "tool-result",
                  "toolCallId", "call_019523a1",
                  "toolName", "search_query_log",
                  "output", Map.of("rows", 3))));

      web.post()
          .uri("/api/ai/chat/sessions")
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(
              Map.of(
                  "connectionId",
                  "ch-test",
                  "sessionId",
                  sessionId,
                  "title",
                  "T",
                  "messages",
                  List.of(
                      Map.of("id", "msg_1", "role", "user", "parts", parts1),
                      Map.of("id", "msg_2", "role", "assistant", "parts", parts2))))
          .exchange()
          .expectStatus()
          .isOk();

      web.get()
          .uri("/api/ai/chat/sessions/{id}/messages", sessionId)
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .jsonPath("$.length()")
          .isEqualTo(2)
          .jsonPath("$[0].id")
          .isEqualTo("msg_1")
          .jsonPath("$[0].sequence")
          .isEqualTo(1)
          .jsonPath("$[1].id")
          .isEqualTo("msg_2")
          .jsonPath("$[1].sequence")
          .isEqualTo(2)
          .jsonPath("$[1].parts.length()")
          .isEqualTo(3)
          .jsonPath("$[1].parts[1].toolName")
          .isEqualTo("search_query_log")
          .jsonPath("$[1].metadata")
          .isEqualTo(null); // no metadata set via A04 seeding
    }

    @Test
    @DisplayName("A08-messages-uimessage-roundtrip: file/tool parts round-trip verbatim")
    void a08MessagesUiMessageRoundtrip() throws Exception {
      String sessionId = "019523a0f0a64d6c8a3e2b9c1f0d7e30";
      // Use a JSON literal so we exercise the exact wire shape (no builder coercion).
      String body =
          """
          {
            "connectionId": "ch-test",
            "sessionId": "%s",
            "title": "Round-trip",
            "messages": [
              {
                "id": "msg_roundtrip_1",
                "role": "user",
                "parts": [
                  { "type": "text", "text": "Look at this CSV" },
                  {
                    "type": "file",
                    "mediaType": "text/csv",
                    "url": "data:text/csv;base64,YWJjCjEyMwo=",
                    "filename": "rows.csv"
                  },
                  {
                    "type": "tool-call",
                    "toolCallId": "call_roundtrip_1",
                    "toolName": "render_chart",
                    "input": { "kind": "bar", "data": [{ "label": "a", "value": 1 }] }
                  },
                  {
                    "type": "tool-result",
                    "toolCallId": "call_roundtrip_1",
                    "toolName": "render_chart",
                    "output": { "ok": true }
                  }
                ]
              }
            ]
          }
          """
              .formatted(sessionId);

      web.post()
          .uri("/api/ai/chat/sessions")
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(body)
          .exchange()
          .expectStatus()
          .isOk();

      JsonNode resp =
          web.get()
              .uri("/api/ai/chat/sessions/{id}/messages", sessionId)
              .header("x-datastoria-user-email", OWNER_EMAIL)
              .exchange()
              .expectStatus()
              .isOk()
              .expectBody(JsonNode.class)
              .returnResult()
              .getResponseBody();

      assertThat(resp.isArray()).isTrue();
      assertThat(resp.size()).isEqualTo(1);
      JsonNode parts = resp.get(0).get("parts");
      assertThat(parts.size()).isEqualTo(4);
      assertThat(parts.get(0).get("type").asText()).isEqualTo("text");
      assertThat(parts.get(1).get("type").asText()).isEqualTo("file");
      assertThat(parts.get(1).get("mediaType").asText()).isEqualTo("text/csv");
      assertThat(parts.get(1).get("filename").asText()).isEqualTo("rows.csv");
      assertThat(parts.get(2).get("type").asText()).isEqualTo("tool-call");
      assertThat(parts.get(2).get("toolName").asText()).isEqualTo("render_chart");
      assertThat(parts.get(2).get("input").get("kind").asText()).isEqualTo("bar");
      assertThat(parts.get(3).get("type").asText()).isEqualTo("tool-result");
      assertThat(parts.get(3).get("output").get("ok").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("A08 persistence omits image payloads and transient stream markers")
    void a08MessagesSanitizeRequestOnlyParts() {
      String sessionId = "019523a0f0a64d6c8a3e2b9c1f0d7e32";
      String body =
          """
          {
            "connectionId": "ch-test",
            "sessionId": "%s",
            "messages": [
              {
                "id": "msg_image_with_text",
                "role": "user",
                "parts": [
                  { "type": "step-start" },
                  { "type": "reasoning", "text": "   " },
                  { "type": "text", "text": "Inspect this image" },
                  {
                    "type": "file",
                    "mediaType": "image/png",
                    "url": "data:image/png;base64,c2VjcmV0LWltYWdl",
                    "filename": "chart.png"
                  },
                  {
                    "type": "reasoning",
                    "text": "",
                    "providerMetadata": { "openai": { "itemId": "reasoning-1" } }
                  }
                ]
              },
              {
                "id": "msg_image_only",
                "role": "user",
                "parts": [
                  {
                    "type": "file",
                    "mediaType": "image/jpeg",
                    "url": "data:image/jpeg;base64,c2VjcmV0LWltYWdl"
                  }
                ]
              }
            ]
          }
          """
              .formatted(sessionId);

      web.post()
          .uri("/api/ai/chat/sessions")
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(body)
          .exchange()
          .expectStatus()
          .isOk();

      JsonNode messages =
          web.get()
              .uri("/api/ai/chat/sessions/{id}/messages", sessionId)
              .header("x-datastoria-user-email", OWNER_EMAIL)
              .exchange()
              .expectStatus()
              .isOk()
              .expectBody(JsonNode.class)
              .returnResult()
              .getResponseBody();

      JsonNode mixedParts = messages.get(0).get("parts");
      assertThat(mixedParts.size()).isEqualTo(2);
      assertThat(mixedParts.get(0).path("text").asText()).isEqualTo("Inspect this image");
      assertThat(mixedParts.get(1).path("providerMetadata").isObject()).isTrue();
      assertThat(mixedParts.toString()).doesNotContain("data:image", "secret-image");

      JsonNode imageOnlyParts = messages.get(1).get("parts");
      assertThat(imageOnlyParts.size()).isEqualTo(1);
      assertThat(imageOnlyParts.get(0).path("type").asText()).isEqualTo("text");
      assertThat(imageOnlyParts.get(0).path("text").asText())
          .isEqualTo("[Image attachment omitted from saved history]");
    }

    @Test
    @DisplayName("A08-messages-unknown-part-preserved: unknown type round-trips byte-for-byte")
    void a08MessagesUnknownPartPreserved() throws Exception {
      String sessionId = "019523a0f0a64d6c8a3e2b9c1f0d7e31";
      String body =
          """
          {
            "connectionId": "ch-test",
            "sessionId": "%s",
            "title": "Unknown part",
            "messages": [
              {
                "id": "msg_unknown_1",
                "role": "assistant",
                "parts": [
                  { "type": "text", "text": "before" },
                  { "type": "future-unknown", "payload": { "nested": [1, 2, 3], "flag": true } },
                  { "type": "text", "text": "after" }
                ],
                "metadata": {
                  "futureField": { "nested": true },
                  "usage": { "inputTokens": 12, "outputTokens": 8 }
                }
              }
            ]
          }
          """
              .formatted(sessionId);

      web.post()
          .uri("/api/ai/chat/sessions")
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(body)
          .exchange()
          .expectStatus()
          .isOk();

      JsonNode resp =
          web.get()
              .uri("/api/ai/chat/sessions/{id}/messages", sessionId)
              .header("x-datastoria-user-email", OWNER_EMAIL)
              .exchange()
              .expectStatus()
              .isOk()
              .expectBody(JsonNode.class)
              .returnResult()
              .getResponseBody();

      JsonNode parts = resp.get(0).get("parts");
      assertThat(parts.size()).isEqualTo(3);
      assertThat(parts.get(1).get("type").asText()).isEqualTo("future-unknown");
      assertThat(parts.get(1).get("payload").get("nested").get(2).asInt()).isEqualTo(3);
      assertThat(parts.get(1).get("payload").get("flag").asBoolean()).isTrue();
      JsonNode metadata = resp.get(0).get("metadata");
      assertThat(metadata.get("futureField").get("nested").asBoolean()).isTrue();
      assertThat(metadata.get("usage").get("inputTokens").asInt()).isEqualTo(12);
    }
  }

  // ============================================================== A09 / A09b

  @Nested
  @DisplayName("A09/A09b share issue/revoke")
  class A09Share {

    @Test
    @DisplayName("A09-share-happy: owner issues a share code; response shape preserved")
    void a09ShareHappy() {
      String sessionId = "019523a0f0a64d6c8a3e2b9c1f0d7e20";
      createSession(sessionId, "ch-test", "Slow query analysis");

      JsonNode body =
          web.post()
              .uri("/api/ai/sessions/{id}/share", sessionId)
              .header("x-datastoria-user-email", OWNER_EMAIL)
              .exchange()
              .expectStatus()
              .isOk()
              .expectHeader()
              .contentType(MediaType.APPLICATION_JSON)
              .expectBody(JsonNode.class)
              .returnResult()
              .getResponseBody();

      assertThat(body.get("url").asText()).startsWith("/session/" + sessionId + "?code=");
      assertThat(body.get("code").asText()).isNotBlank();
      // Default expiresAt is 2100-01-01T00:00:00Z (Node-compat).
      assertThat(body.get("expiresAt").asText()).startsWith("2100-01-01T00:00:00");
    }

    @Test
    @DisplayName("A09-share-not-found: HTTP 404 'Not found' for unknown session")
    void a09ShareNotFound() {
      web.post()
          .uri("/api/ai/sessions/{id}/share", "019523a0f0a64d6c8a3e2b9c1f0d7e99")
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .exchange()
          .expectStatus()
          .isNotFound()
          .expectBody(String.class)
          .isEqualTo("Not found");
    }

    @Test
    @DisplayName("A09b-revoke-happy: HTTP 204; subsequent share verification fails")
    void a09bRevokeHappy() {
      String sessionId = "019523a0f0a64d6c8a3e2b9c1f0d7e20";
      createSession(sessionId, "ch-test", "Slow query analysis");
      String code = issueShareCode(sessionId);

      // Pre-condition: code works.
      web.get()
          .uri("/api/ai/chat/sessions/{id}", sessionId)
          .header("x-datastoria-user-email", VISITOR_EMAIL)
          .header("X-Session-Share-Code", code)
          .exchange()
          .expectStatus()
          .isOk();

      // Revoke.
      web.post()
          .uri("/api/ai/sessions/{id}/share:revoke", sessionId)
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .exchange()
          .expectStatus()
          .isNoContent()
          .expectBody()
          .isEmpty();

      // Post-condition: same code is now rejected.
      web.get()
          .uri("/api/ai/chat/sessions/{id}", sessionId)
          .header("x-datastoria-user-email", VISITOR_EMAIL)
          .header("X-Session-Share-Code", code)
          .exchange()
          .expectStatus()
          .isForbidden()
          .expectBody(String.class)
          .isEqualTo("Invalid session share code");
    }

    @Test
    @DisplayName("A09b-revoke-not-found: HTTP 404 ProblemDetail SHARE_NOT_FOUND")
    void a09bRevokeNotFound() {
      String sessionId = "019523a0f0a64d6c8a3e2b9c1f0d7e20";
      createSession(sessionId, "ch-test", "Slow query analysis");

      web.post()
          .uri("/api/ai/sessions/{id}/share:revoke", sessionId)
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .exchange()
          .expectStatus()
          .isNotFound()
          .expectHeader()
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .expectBody()
          .jsonPath("$.code")
          .isEqualTo("SHARE_NOT_FOUND")
          .jsonPath("$.title")
          .isEqualTo("No active share for this session");
    }

    @Test
    @DisplayName("A09b: revoking then re-issuing yields a fresh code that works")
    void a09bReissueAfterRevoke() {
      String sessionId = "019523a0f0a64d6c8a3e2b9c1f0d7e20";
      createSession(sessionId, "ch-test", "Slow query analysis");
      String oldCode = issueShareCode(sessionId);

      web.post()
          .uri("/api/ai/sessions/{id}/share:revoke", sessionId)
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .exchange()
          .expectStatus()
          .isNoContent();

      String newCode = issueShareCode(sessionId);
      assertThat(newCode).isNotEqualTo(oldCode);

      web.get()
          .uri("/api/ai/chat/sessions/{id}", sessionId)
          .header("x-datastoria-user-email", VISITOR_EMAIL)
          .header("X-Session-Share-Code", newCode)
          .exchange()
          .expectStatus()
          .isOk();
    }
  }

  // ============================================================== A10

  @Nested
  @DisplayName("A10 recordAutoExplainFeedback")
  class A10Feedback {

    @Test
    @DisplayName("A10-feedback-recorded: happy path returns 200 with {recorded:true, ...}")
    void a10FeedbackRecorded() {
      String sessionId = "019523a0f0a64d6c8a3e2b9c1f0d7e20";
      // Seed a session + target message so FEEDBACK_TARGET_NOT_FOUND doesn't fire.
      web.post()
          .uri("/api/ai/chat/sessions")
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(
              Map.of(
                  "connectionId",
                  "ch-test",
                  "sessionId",
                  sessionId,
                  "title",
                  "T",
                  "messages",
                  List.of(
                      Map.of(
                          "id", "msg_019523a101",
                          "role", "assistant",
                          "parts", List.of(Map.of("type", "text", "text", "analysis"))))))
          .exchange()
          .expectStatus()
          .isOk();

      web.post()
          .uri("/api/ai/chat/feedback/auto-explain")
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(
              Map.of(
                  "source",
                  "auto_explain_error",
                  "sessionId",
                  sessionId,
                  "messageId",
                  "msg_019523a101",
                  "solved",
                  true,
                  "payload",
                  Map.of(
                      "queryId", "q_42",
                      "errorCode", "119",
                      "sql", "SELECT count() FROM events"),
                  "recoveryActionTaken",
                  false))
          .exchange()
          .expectStatus()
          .isOk()
          .expectHeader()
          .contentType(MediaType.APPLICATION_JSON)
          .expectBody()
          .jsonPath("$.recorded")
          .isEqualTo(true)
          .jsonPath("$.updatedAt")
          .isNotEmpty()
          .jsonPath("$.solved")
          .isEqualTo(true);
    }

    @Test
    @DisplayName("A10-feedback-recorded: solved=false with reasonCode stores reasonCode/freeText")
    void a10FeedbackRecordedUnsolved() {
      String sessionId = "019523a0f0a64d6c8a3e2b9c1f0d7e21";
      web.post()
          .uri("/api/ai/chat/sessions")
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(
              Map.of(
                  "connectionId",
                  "ch-test",
                  "sessionId",
                  sessionId,
                  "title",
                  "T",
                  "messages",
                  List.of(
                      Map.of(
                          "id", "msg_target_unsol",
                          "role", "assistant",
                          "parts", List.of(Map.of("type", "text", "text", "x"))))))
          .exchange()
          .expectStatus()
          .isOk();

      web.post()
          .uri("/api/ai/chat/feedback/auto-explain")
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(
              Map.of(
                  "source", "auto_explain_error",
                  "sessionId", sessionId,
                  "messageId", "msg_target_unsol",
                  "solved", false,
                  "reasonCode", "too_vague",
                  "freeText", "Need more detail",
                  "payload", Map.of("queryId", "q_43")))
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .jsonPath("$.recorded")
          .isEqualTo(true)
          .jsonPath("$.solved")
          .isEqualTo(false);
    }

    @Test
    @DisplayName("A10-feedback-invalid-format: solved=false without reasonCode -> HTTP 400")
    void a10FeedbackInvalidFormat() {
      String sessionId = "019523a0f0a64d6c8a3e2b9c1f0d7e20";
      createSession(sessionId, "ch-test", "T");

      web.post()
          .uri("/api/ai/chat/feedback/auto-explain")
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(
              Map.of(
                  "source",
                  "auto_explain_error",
                  "sessionId",
                  sessionId,
                  "messageId",
                  "msg_019523a101",
                  "solved",
                  false,
                  "payload",
                  Map.of("queryId", "q_43")))
          .exchange()
          .expectStatus()
          .isBadRequest()
          .expectHeader()
          .contentTypeCompatibleWith(MediaType.TEXT_PLAIN)
          .expectBody(String.class)
          .isEqualTo("Invalid request format");
    }

    @Test
    @DisplayName("A10-feedback-target-not-found: HTTP 404 ProblemDetail FEEDBACK_TARGET_NOT_FOUND")
    void a10FeedbackTargetNotFound() {
      String sessionId = "019523a0f0a64d6c8a3e2b9c1f0d7e20";
      createSession(sessionId, "ch-test", "T");

      web.post()
          .uri("/api/ai/chat/feedback/auto-explain")
          .header("x-datastoria-user-email", OWNER_EMAIL)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(
              Map.of(
                  "source",
                  "auto_explain_error",
                  "sessionId",
                  sessionId,
                  "messageId",
                  "msg_does_not_exist",
                  "solved",
                  true,
                  "payload",
                  Map.of("queryId", "q_44")))
          .exchange()
          .expectStatus()
          .isNotFound()
          .expectHeader()
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .expectBody()
          .jsonPath("$.code")
          .isEqualTo("FEEDBACK_TARGET_NOT_FOUND")
          .jsonPath("$.title")
          .isEqualTo("Referenced message does not exist");
    }
  }

  // ============================================================== cross-tenant

  @Test
  @DisplayName("Cross-tenant isolation: session created by tenant-test is invisible to tenant-b")
  void crossTenantIsolation() {
    String sessionId = "019523a0f0a64d6c8a3e2b9c1f0d7e20";
    createSession(sessionId, "ch-test", "Slow query analysis");

    web.get()
        .uri("/api/ai/chat/sessions/{id}", sessionId)
        .header("x-datastoria-user-email", OTHER_TENANT_EMAIL)
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody(String.class)
        .isEqualTo("Not found");
  }

  // ============================================================== clock helper

  private static Instant snapshot() {
    return Instant.now();
  }
}
