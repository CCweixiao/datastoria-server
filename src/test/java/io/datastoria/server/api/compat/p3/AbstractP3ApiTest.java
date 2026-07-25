package io.datastoria.server.api.compat.p3;

import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.datastoria.server.TestDbHelper;

/**
 * Base class for P3 API tests. Establishes the standard {@link WebTestClient} + {@link
 * TestDbHelper} pair and exposes the common dev-user header used by every authenticated fixture.
 *
 * <p>Subclasses opt into alternate configurations ({@code allow-anonymous=false}, {@code
 * store-enabled=false}) via their own {@code @TestPropertySource}/{@code DynamicPropertyRegistry}.
 */
@ActiveProfiles("test")
public abstract class AbstractP3ApiTest {

  protected static final String OWNER_EMAIL = "dev@example.com";
  protected static final String VISITOR_EMAIL = "visitor@example.com";
  protected static final String OTHER_TENANT_EMAIL = "tenant-b@example.com";

  @Autowired protected WebTestClient web;
  @Autowired protected TestDbHelper dbHelper;
  @Autowired protected ObjectMapper objectMapper;
  @Autowired protected ApplicationContext applicationContext;

  /**
   * Creates a session via A04 with no messages; returns the assigned {@code chatId}. Test fixtures
   * pin specific session ids, so callers should pass the desired id verbatim.
   */
  protected String createSession(String sessionId, String connectionId, String title) {
    Map<String, Object> body =
        title == null
            ? Map.of("connectionId", connectionId, "sessionId", sessionId)
            : Map.of("connectionId", connectionId, "sessionId", sessionId, "title", title);
    return createSessionRaw(body);
  }

  /** Creates a session with the supplied initial messages. */
  protected String createSessionWithMessages(
      String sessionId, String connectionId, String title, JsonNode messages) {
    Map<String, Object> body =
        Map.of(
            "connectionId", connectionId,
            "sessionId", sessionId,
            "title", title == null ? "T" : title,
            "messages", messages);
    return createSessionRaw(body);
  }

  private String createSessionRaw(Map<String, Object> body) {
    return web.post()
        .uri("/api/ai/chat/sessions")
        .header("x-datastoria-user-email", OWNER_EMAIL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(JsonNode.class)
        .returnResult()
        .getResponseBody()
        .get("session")
        .get("chatId")
        .asText();
  }

  /** Issues a share for the session and returns the JWT share code. */
  protected String issueShareCode(String sessionId) {
    return web.post()
        .uri("/api/ai/sessions/{id}/share", sessionId)
        .header("x-datastoria-user-email", OWNER_EMAIL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(JsonNode.class)
        .returnResult()
        .getResponseBody()
        .get("code")
        .asText();
  }

  /** Snapshot the current test clock so assertions can compare ISO-8601 strings. */
  protected static Instant now() {
    return Instant.now();
  }

  /** Marker so subclass @TestConfiguration can be discovered via @ContextConfiguration. */
  @TestConfiguration
  public static class Marker {}
}
