package io.github.ccweixiao.datastoria.dao.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.ccweixiao.datastoria.boot.TestDbHelper;
import io.github.ccweixiao.datastoria.common.domain.ChatMessage;
import io.github.ccweixiao.datastoria.common.domain.ChatSession;

@SpringBootTest
@ActiveProfiles("dev")
class MysqlChatMessageRepositoryTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private static final String TENANT = "tenant-test";
  private static final String USER = "dev@example.com";

  @Autowired ChatSessionRepository sessionRepo;
  @Autowired ChatMessageRepository repo;
  @Autowired TestDbHelper dbHelper;

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
  }

  @Test
  void saveAndFindBySession() {
    seedSession("sess_a");
    repo.save(message("msg_a1", "sess_a", "user", 1, "[{\"type\":\"text\",\"text\":\"hi\"}]"));
    repo.save(
        message("msg_a2", "sess_a", "assistant", 2, "[{\"type\":\"text\",\"text\":\"hi back\"}]"));

    List<ChatMessage> rows = repo.findBySession("sess_a", TENANT);
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).id()).isEqualTo("msg_a1");
    assertThat(rows.get(1).id()).isEqualTo("msg_a2");
    assertThat(rows).allSatisfy(m -> assertThat(m.tenantId()).isEqualTo(TENANT));
  }

  @Test
  void saveIsIdempotentByIdAndUpdatesInPlace() {
    seedSession("sess_b");
    repo.save(message("msg_b1", "sess_b", "user", 1, "[{\"type\":\"text\",\"text\":\"v1\"}]"));
    repo.save(message("msg_b1", "sess_b", "user", 1, "[{\"type\":\"text\",\"text\":\"v2\"}]"));

    List<ChatMessage> rows = repo.findBySession("sess_b", TENANT);
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).partsJson()).contains("v2");
  }

  @Test
  void savePreservesUnknownPartTypes() throws Exception {
    seedSession("sess_c");
    String parts =
        "[{\"type\":\"text\",\"text\":\"x\"},"
            + "{\"type\":\"datastoria-dyn-renderer\",\"renderer\":\"q\",\"version\":3,"
            + "\"data\":{\"nodes\":[{\"id\":\"n1\"}]}}]";
    repo.save(message("msg_c1", "sess_c", "assistant", 1, parts));

    ChatMessage found = repo.findById("msg_c1", TENANT, "sess_c").orElseThrow();
    assertThat(JSON.readTree(found.partsJson())).isEqualTo(JSON.readTree(parts));
  }

  @Test
  void savePreservesMetadataJson() throws Exception {
    seedSession("sess_d");
    String metadata =
        "{\"usage\":{\"promptTokens\":40,\"completionTokens\":12,\"totalTokens\":52}}";
    ChatMessage saved =
        new ChatMessage(
            "msg_d1",
            TENANT,
            "sess_d",
            USER,
            "assistant",
            "[{\"type\":\"text\",\"text\":\"hi\"}]",
            metadata,
            1L,
            null,
            null);
    repo.save(saved);

    ChatMessage found = repo.findById("msg_d1", TENANT, "sess_d").orElseThrow();
    assertThat(JSON.readTree(found.metadataJson())).isEqualTo(JSON.readTree(metadata));
  }

  @Test
  void saveNullMetadataPersistsAndReadsBackAsNull() {
    seedSession("sess_e");
    repo.save(message("msg_e1", "sess_e", "user", 1, "[{\"type\":\"text\",\"text\":\"hi\"}]"));
    ChatMessage found = repo.findById("msg_e1", TENANT, "sess_e").orElseThrow();
    assertThat(found.metadataJson()).isNull();
  }

  @Test
  void findBySessionReturnsEmptyWhenSessionHasNoMessages() {
    seedSession("sess_empty");
    assertThat(repo.findBySession("sess_empty", TENANT)).isEmpty();
  }

  @Test
  void existsReturnsFalseForMissingMessage() {
    seedSession("sess_f");
    repo.save(message("msg_f1", "sess_f", "user", 1, "[{\"type\":\"text\",\"text\":\"hi\"}]"));
    assertThat(repo.exists(TENANT, USER, "sess_f", "msg_f1")).isTrue();
    assertThat(repo.exists(TENANT, USER, "sess_f", "msg_missing")).isFalse();
    assertThat(repo.exists(TENANT, USER, "sess_missing", "msg_f1")).isFalse();
  }

  @Test
  void existsDoesNotLeakCrossTenant() {
    seedSession("sess_g");
    repo.save(message("msg_g1", "sess_g", "user", 1, "[{\"type\":\"text\",\"text\":\"hi\"}]"));
    assertThat(repo.exists("tenant-other", USER, "sess_g", "msg_g1")).isFalse();
  }

  private void seedSession(String id) {
    sessionRepo.save(new ChatSession(id, TENANT, USER, "ch-test", "T", 0L, null, null));
  }

  private ChatMessage message(
      String id, String session, String role, long sequence, String partsJson) {
    return new ChatMessage(id, TENANT, session, USER, role, partsJson, null, sequence, null, null);
  }
}
