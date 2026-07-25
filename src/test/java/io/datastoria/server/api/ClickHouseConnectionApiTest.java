package io.datastoria.server.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fasterxml.jackson.databind.JsonNode;

import io.datastoria.server.TestDbHelper;
import io.datastoria.server.service.ClickHouseRemoteClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ClickHouseConnectionApiTest {

  private static final String IDENTITY_HEADER = "x-datastoria-user-email";

  @Autowired WebTestClient web;
  @Autowired TestDbHelper dbHelper;
  @MockitoBean ClickHouseRemoteClient remoteClient;

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
    when(remoteClient.execute(any(), anyString(), anyString())).thenReturn(Mono.just("{}"));
    when(remoteClient.execute(any(), anyString(), anyString(), any())).thenReturn(Mono.just("{}"));
    when(remoteClient.executeStream(any(), anyString(), anyString(), any()))
        .thenReturn(
            Mono.just(
                new ClickHouseRemoteClient.RemoteQueryResponse(
                    HttpStatus.OK,
                    HttpHeaders.readOnlyHttpHeaders(
                        new HttpHeaders() {
                          {
                            setContentType(MediaType.APPLICATION_JSON);
                            add("X-ClickHouse-Summary", "{\"read_rows\":\"1\"}");
                          }
                        }),
                    Flux.just(
                        DefaultDataBufferFactory.sharedInstance.wrap(
                            "{\"data\":[{\"value\":1}]}"
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8))))));
  }

  @Test
  void connectionCrudPersistsEncryptedCredentialWithoutReturningIt() {
    JsonNode created =
        web.post()
            .uri("/api/connections")
            .header(IDENTITY_HEADER, "dev@example.com")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "name": "local",
                  "url": "http://127.0.0.1:8123",
                  "username": "default",
                  "password": "super-secret-password",
                  "cluster": "default",
                  "enabled": true
                }
                """)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .valueEquals("ETag", "\"0\"")
            .expectBody(JsonNode.class)
            .returnResult()
            .getResponseBody();

    assertThat(created).isNotNull();
    assertThat(created.toString()).doesNotContain("super-secret-password");
    assertThat(created.path("credentialConfigured").asBoolean()).isTrue();
    assertThat(created.path("credentialMaskedHint").asText()).isEqualTo("sup…ord");
    String id = created.path("id").asText();

    web.get()
        .uri("/api/connections")
        .header(IDENTITY_HEADER, "dev@example.com")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$[0].id")
        .isEqualTo(id)
        .jsonPath("$[0].password")
        .doesNotExist();

    web.put()
        .uri("/api/connections/{id}", id)
        .header(IDENTITY_HEADER, "dev@example.com")
        .header("If-Match", "\"0\"")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "name": "renamed",
              "url": "http://127.0.0.1:8123",
              "username": "default",
              "cluster": "default",
              "enabled": true
            }
            """)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.name")
        .isEqualTo("renamed")
        .jsonPath("$.credentialConfigured")
        .isEqualTo(true)
        .jsonPath("$.revision")
        .isEqualTo(1);

    web.delete()
        .uri("/api/connections/{id}", id)
        .header(IDENTITY_HEADER, "dev@example.com")
        .header("If-Match", "\"1\"")
        .exchange()
        .expectStatus()
        .isNoContent();
  }

  @Test
  void connectionIsIsolatedByUserAndRejectsEmbeddedCredentials() {
    String id =
        web.post()
            .uri("/api/connections")
            .header(IDENTITY_HEADER, "dev@example.com")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "name": "local",
                  "url": "http://127.0.0.1:8123",
                  "username": "default",
                  "password": "",
                  "enabled": true
                }
                """)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(JsonNode.class)
            .returnResult()
            .getResponseBody()
            .path("id")
            .asText();

    web.get()
        .uri("/api/connections/{id}", id)
        .header(IDENTITY_HEADER, "other@example.com")
        .exchange()
        .expectStatus()
        .isNotFound();

    web.post()
        .uri("/api/connections")
        .header(IDENTITY_HEADER, "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "name": "unsafe",
              "url": "http://user:password@127.0.0.1:8123",
              "username": "default",
              "enabled": true
            }
            """)
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void transientConnectionTestDoesNotPersistCredential() {
    web.post()
        .uri("/api/connections/test")
        .header(IDENTITY_HEADER, "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "name": "temporary",
              "url": "http://127.0.0.1:8123",
              "username": "default",
              "password": "only-for-test",
              "enabled": true
            }
            """)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.ok")
        .isEqualTo(true);

    web.get()
        .uri("/api/connections")
        .header(IDENTITY_HEADER, "dev@example.com")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json("[]");
  }

  @Test
  void queryStreamsClickHouseBodyAndResponseHeaders() {
    String id =
        web.post()
            .uri("/api/connections")
            .header(IDENTITY_HEADER, "dev@example.com")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "name": "local",
                  "url": "http://127.0.0.1:8123",
                  "username": "default",
                  "password": "",
                  "enabled": true
                }
                """)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(JsonNode.class)
            .returnResult()
            .getResponseBody()
            .path("id")
            .asText();

    web.post()
        .uri("/api/connections/{id}/query", id)
        .header(IDENTITY_HEADER, "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"query\":\"SELECT 1 FORMAT JSON\",\"parameters\":{}}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectHeader()
        .valueEquals("X-ClickHouse-Summary", "{\"read_rows\":\"1\"}")
        .expectBody()
        .jsonPath("$.data[0].value")
        .isEqualTo(1);
  }

  @Test
  void nodeQueryIsWrappedBySpringWithStoredCredential() {
    String id =
        web.post()
            .uri("/api/connections")
            .header(IDENTITY_HEADER, "dev@example.com")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "name": "cluster",
                  "url": "http://127.0.0.1:8123",
                  "username": "external",
                  "password": "server-secret",
                  "cluster": "prod",
                  "enabled": true
                }
                """)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(JsonNode.class)
            .returnResult()
            .getResponseBody()
            .path("id")
            .asText();

    web.post()
        .uri("/api/connections/{id}/query", id)
        .header(IDENTITY_HEADER, "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "query": "SELECT 1",
              "parameters": {},
              "targetNode": "node-1.example",
              "targetUser": "internal"
            }
            """)
        .exchange()
        .expectStatus()
        .isOk();

    ArgumentCaptor<String> password = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
    verify(remoteClient).executeStream(any(), password.capture(), query.capture(), any());
    assertThat(password.getValue()).isEqualTo("server-secret");
    assertThat(query.getValue())
        .contains("remote(")
        .contains("'node-1.example'")
        .contains("'internal'")
        .contains("'server-secret'")
        .contains("SELECT 1");
  }

  @Test
  void connectionTemplatesMatchesOriginalEmptyCatalog() {
    web.get()
        .uri("/api/connections/templates")
        .header(IDENTITY_HEADER, "dev@example.com")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json("[]");
  }
}
