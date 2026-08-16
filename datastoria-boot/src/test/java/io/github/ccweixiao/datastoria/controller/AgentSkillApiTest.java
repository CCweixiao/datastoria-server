package io.github.ccweixiao.datastoria.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Read-only contract for the classpath Skill catalog: skills ship in the jar, so the API exposes
 * only list / detail / resource / commands and every write verb is gone.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class AgentSkillApiTest {

  private static final String USER_HEADER = "x-datastoria-user-email";
  private static final String USER = "dev@example.com";

  @Autowired WebTestClient web;

  @Test
  void listReturnsPublishedBuiltinCatalog() {
    web.get()
        .uri("/api/ai/skills")
        .header(USER_HEADER, USER)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.length()")
        .isEqualTo(9)
        .jsonPath("$[0].source")
        .isEqualTo("builtin")
        .jsonPath("$[0].state")
        .isEqualTo("published")
        .jsonPath("$[0].scope")
        .isEqualTo("global");

    // Every entry is a published builtin with the shared catalog fields.
    web.get()
        .uri("/api/ai/skills")
        .header(USER_HEADER, USER)
        .exchange()
        .expectBody()
        .jsonPath("$[?(@.id == 'clickhouse')].name")
        .isEqualTo("clickhouse-best-practices")
        .jsonPath("$[?(@.id == 'clickhouse')].hasResources")
        .isEqualTo(true);
  }

  @Test
  void detailExposesContentAndResourcePaths() {
    web.get()
        .uri("/api/ai/skills/clickhouse")
        .header(USER_HEADER, USER)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.source")
        .isEqualTo("builtin")
        .jsonPath("$.content")
        .value(content -> ((String) content).startsWith("---"))
        .jsonPath("$.hasResources")
        .isEqualTo(true);
  }

  @Test
  void resourceServesBundleFile() {
    web.get()
        .uri(
            b ->
                b.path("/api/ai/skills/clickhouse/resource")
                    .queryParam("path", "README.md")
                    .build())
        .header(USER_HEADER, USER)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.content")
        .value(content -> ((String) content).contains("ClickHouse Best Practices"))
        .jsonPath("$.source")
        .isEqualTo("builtin");
  }

  @Test
  void unknownSkillOrPathIsNotFound() {
    web.get()
        .uri("/api/ai/skills/does-not-exist")
        .header(USER_HEADER, USER)
        .exchange()
        .expectStatus()
        .isNotFound();
    web.get()
        .uri(
            b ->
                b.path("/api/ai/skills/clickhouse/resource")
                    .queryParam("path", "no/such.md")
                    .build())
        .header(USER_HEADER, USER)
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @Test
  void writeEndpointsAreGone() {
    web.post()
        .uri("/api/ai/skills")
        .header(USER_HEADER, USER)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"id\":\"x\",\"content\":\"---\\nname: x\\ndescription: d\\n---\\nbody\"}")
        .exchange()
        .expectStatus()
        .isEqualTo(405);
    web.patch()
        .uri("/api/ai/skills/clickhouse")
        .header(USER_HEADER, USER)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"action\":\"publish\"}")
        .exchange()
        .expectStatus()
        .isEqualTo(405);
    web.delete()
        .uri("/api/ai/skills/clickhouse")
        .header(USER_HEADER, USER)
        .exchange()
        .expectStatus()
        .isEqualTo(405);
    // The review action controller was deleted outright, not just its method.
    web.post()
        .uri("/api/ai/skills/actions/review")
        .header(USER_HEADER, USER)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"skillId\":\"clickhouse\"}")
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @Test
  void commandsExposeSlashCommandCatalog() {
    web.get()
        .uri("/api/ai/commands")
        .header(USER_HEADER, USER)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$[0].name")
        .value(name -> ((String) name).matches("[a-z][a-z0-9_-]*"))
        .jsonPath("$[?(@.name == 'clickhouse-best-practices')].skillId")
        .isEqualTo("clickhouse");
  }
}
