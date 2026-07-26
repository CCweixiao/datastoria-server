package io.datastoria.server.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import io.datastoria.server.TestDbHelper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UserStateApiTest {

  @Autowired WebTestClient web;
  @Autowired TestDbHelper dbHelper;

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
  }

  @Test
  void persistsJsonWithOptimisticRevisionAndUserIsolation() {
    web.put()
        .uri("/api/me/state/theme/current")
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"value\":{\"mode\":\"dark\"}}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("ETag", "\"0\"")
        .expectBody()
        .jsonPath("$.value.mode")
        .isEqualTo("dark");

    web.put()
        .uri("/api/me/state/theme/current")
        .header("x-datastoria-user-email", "dev@example.com")
        .header("If-Match", "\"0\"")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"value\":{\"mode\":\"light\"}}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.revision")
        .isEqualTo(1);

    web.get()
        .uri("/api/me/state/theme")
        .header("x-datastoria-user-email", "other@example.com")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json("[]");

    web.delete()
        .uri("/api/me/state/theme/current")
        .header("x-datastoria-user-email", "dev@example.com")
        .exchange()
        .expectStatus()
        .isNoContent();
  }

  @Test
  void supportsLastWriteWinsWithoutIfMatchAndStillRejectsStaleExplicitRevision() {
    web.put()
        .uri("/api/me/state/query-draft/sql:input")
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"value\":{\"sql\":\"SELECT 1\"}}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("ETag", "\"0\"");

    web.put()
        .uri("/api/me/state/query-draft/sql:input")
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"value\":{\"sql\":\"SELECT 2\"}}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("ETag", "\"1\"")
        .expectBody()
        .jsonPath("$.value.sql")
        .isEqualTo("SELECT 2");

    web.put()
        .uri("/api/me/state/query-draft/sql:input")
        .header("x-datastoria-user-email", "dev@example.com")
        .header("If-Match", "\"0\"")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"value\":{\"sql\":\"SELECT 3\"}}")
        .exchange()
        .expectStatus()
        .isEqualTo(409);
  }
}
