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
class AgentSkillApiTest {

  @Autowired WebTestClient web;
  @Autowired TestDbHelper dbHelper;

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
  }

  @Test
  void draftPublishResourceAndCommandLifecycle() {
    web.post()
        .uri("/api/ai/skills")
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "id": "diagnose",
              "content": "---\\nname: diagnose\\ndescription: Diagnose ClickHouse\\n---\\nFind the cause.",
              "scope": "self",
              "state": "draft",
              "resources": [{"path":"references/rules.md","content":"Use evidence."}]
            }
            """)
        .exchange()
        .expectStatus()
        .isCreated();

    web.get()
        .uri("/api/ai/skills")
        .header("x-datastoria-user-email", "dev@example.com")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json("[]");

    web.get()
        .uri("/api/ai/skills?includeDraft=true")
        .header("x-datastoria-user-email", "dev@example.com")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$[0].name")
        .isEqualTo("diagnose")
        .jsonPath("$[0].hasResources")
        .isEqualTo(true);

    web.get()
        .uri(
            uriBuilder ->
                uriBuilder
                    .path("/api/ai/skills/diagnose/resource")
                    .queryParam("path", "references/rules.md")
                    .queryParam("includeDraft", true)
                    .build())
        .header("x-datastoria-user-email", "dev@example.com")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.content")
        .isEqualTo("Use evidence.");

    web.patch()
        .uri("/api/ai/skills/diagnose")
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"action\":\"publish\"}")
        .exchange()
        .expectStatus()
        .isOk();

    web.get()
        .uri("/api/ai/commands")
        .header("x-datastoria-user-email", "dev@example.com")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$[0].name")
        .isEqualTo("diagnose")
        .jsonPath("$[0].skillId")
        .isEqualTo("diagnose");
  }

  @Test
  void selfScopedSkillIsUserIsolatedAndUnsafeResourcePathIsRejected() {
    web.post()
        .uri("/api/ai/skills")
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "id": "private-skill",
              "content": "---\\nname: private-skill\\ndescription: Private\\n---\\nPrivate.",
              "scope": "self",
              "state": "published"
            }
            """)
        .exchange()
        .expectStatus()
        .isCreated();

    web.get()
        .uri("/api/ai/skills/private-skill")
        .header("x-datastoria-user-email", "other@example.com")
        .exchange()
        .expectStatus()
        .isNotFound();

    web.patch()
        .uri("/api/ai/skills/private-skill")
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "content": "---\\nname: private-skill\\n---\\nPrivate.",
              "resources": [{"path":"../secret","content":"bad"}]
            }
            """)
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void publishedRevisionAndResourcesStayVisibleWhileNewDraftIsEdited() {
    web.post()
        .uri("/api/ai/skills")
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "id": "versioned",
              "content": "---\\nname: versioned\\ndescription: Version one\\n---\\nPublished body.",
              "scope": "self",
              "state": "draft",
              "resources": [{"path":"references/rules.md","content":"published resource"}]
            }
            """)
        .exchange()
        .expectStatus()
        .isCreated();
    publish("versioned");

    web.patch()
        .uri("/api/ai/skills/versioned")
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "content": "---\\nname: versioned\\ndescription: Version two\\n---\\nDraft body.",
              "state": "draft",
              "resources": [{"path":"references/rules.md","content":"draft resource"}]
            }
            """)
        .exchange()
        .expectStatus()
        .isOk();

    web.get()
        .uri("/api/ai/skills/versioned")
        .header("x-datastoria-user-email", "dev@example.com")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.description")
        .isEqualTo("Version one")
        .jsonPath("$.content")
        .isEqualTo("---\nname: versioned\ndescription: Version one\n---\nPublished body.");
    expectResource("versioned", false, "published resource");

    web.get()
        .uri("/api/ai/skills/versioned?includeDraft=true")
        .header("x-datastoria-user-email", "dev@example.com")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.description")
        .isEqualTo("Version two")
        .jsonPath("$.content")
        .isEqualTo("---\nname: versioned\ndescription: Version two\n---\nDraft body.");
    expectResource("versioned", true, "draft resource");

    publish("versioned");
    expectResource("versioned", false, "draft resource");
  }

  private void publish(String id) {
    web.patch()
        .uri("/api/ai/skills/" + id)
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"action\":\"publish\"}")
        .exchange()
        .expectStatus()
        .isOk();
  }

  private void expectResource(String id, boolean includeDraft, String content) {
    web.get()
        .uri(
            uriBuilder ->
                uriBuilder
                    .path("/api/ai/skills/" + id + "/resource")
                    .queryParam("path", "references/rules.md")
                    .queryParam("includeDraft", includeDraft)
                    .build())
        .header("x-datastoria-user-email", "dev@example.com")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.content")
        .isEqualTo(content);
  }
}
