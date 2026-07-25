package io.datastoria.server.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import io.datastoria.server.TestDbHelper;
import io.datastoria.server.domain.ChatMessage;
import io.datastoria.server.domain.ChatSession;
import io.datastoria.server.domain.FeedbackEvent;

@SpringBootTest
@ActiveProfiles("test")
class SqliteFeedbackEventRepositoryTest {

  private static final String TENANT = "tenant-test";
  private static final String USER = "dev@example.com";

  @Autowired ChatSessionRepository sessionRepo;
  @Autowired ChatMessageRepository messageRepo;
  @Autowired FeedbackEventRepository repo;
  @Autowired TestDbHelper dbHelper;

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
  }

  @Test
  void upsertInsertsThenUpdatesByNaturalKey() {
    seedMessage("sess_a", "msg_a1");

    FeedbackEvent first =
        new FeedbackEvent(
            "fb_a1",
            TENANT,
            USER,
            "auto_explain_error",
            "sess_a",
            "msg_a1",
            false,
            "too_vague",
            "{\"queryId\":\"q_1\"}",
            "need more detail",
            false,
            null,
            null);
    FeedbackEvent saved = repo.upsert(first);
    assertThat(saved.solved()).isFalse();
    assertThat(saved.reasonCode()).isEqualTo("too_vague");

    // Same key, different content.
    FeedbackEvent second =
        new FeedbackEvent(
            "fb_a1_v2",
            TENANT,
            USER,
            "auto_explain_error",
            "sess_a",
            "msg_a1",
            true,
            null,
            "{\"queryId\":\"q_1\"}",
            null,
            false,
            null,
            null);
    FeedbackEvent updated = repo.upsert(second);

    assertThat(updated.id()).isEqualTo("fb_a1"); // original id retained
    assertThat(updated.solved()).isTrue();
    assertThat(updated.reasonCode()).isNull();
    assertThat(updated.freeText()).isNull();
  }

  @Test
  void findReturnsEmptyForUnknownKey() {
    seedMessage("sess_b", "msg_b1");
    assertThat(repo.find(TENANT, USER, "auto_explain_error", "sess_b", "msg_missing")).isEmpty();
  }

  @Test
  void recoveryActionTakenPersistsAndReadsBack() {
    seedMessage("sess_c", "msg_c1");
    FeedbackEvent e =
        new FeedbackEvent(
            "fb_c1",
            TENANT,
            USER,
            "auto_explain_error",
            "sess_c",
            "msg_c1",
            false,
            "unsafe_fix",
            "{\"queryId\":\"q_1\"}",
            null,
            true,
            null,
            null);
    FeedbackEvent saved = repo.upsert(e);
    assertThat(saved.recoveryActionTaken()).isTrue();
  }

  private void seedMessage(String sessionId, String messageId) {
    sessionRepo.save(new ChatSession(sessionId, TENANT, USER, "ch-test", "T", 0L, null, null));
    messageRepo.save(
        new ChatMessage(
            messageId,
            TENANT,
            sessionId,
            USER,
            "assistant",
            "[{\"type\":\"text\",\"text\":\"hi\"}]",
            null,
            1L,
            null,
            null));
  }
}
