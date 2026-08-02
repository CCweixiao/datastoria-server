package io.github.ccweixiao.datastoria.dao.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

  // ---------- saveInitialMessages: idempotent batch insert ----------

  @Test
  void saveInitialMessagesAssignsContiguousSequences() {
    seedSession("sess_h");
    repo.saveInitialMessages(TENANT, "sess_h", USER, textInputs(ids("h", 3)));

    List<ChatMessage> rows = repo.findBySession("sess_h", TENANT);
    assertThat(rows).extracting(ChatMessage::id).containsExactly("h0", "h1", "h2");
    assertThat(rows).extracting(ChatMessage::sequence).containsExactly(1L, 2L, 3L);
  }

  @Test
  void saveInitialMessagesIsIdempotentByMessageId() {
    seedSession("sess_i");
    repo.saveInitialMessages(TENANT, "sess_i", USER, textInputs(ids("i", 3)));
    // A client retry re-sends the same ids — must be a no-op, not a duplicate-key error.
    repo.saveInitialMessages(TENANT, "sess_i", USER, textInputs(ids("i", 3)));

    List<ChatMessage> rows = repo.findBySession("sess_i", TENANT);
    assertThat(rows).extracting(ChatMessage::id).containsExactly("i0", "i1", "i2");
    assertThat(rows).extracting(ChatMessage::sequence).containsExactly(1L, 2L, 3L);
  }

  @Test
  void saveInitialMessagesAppendsPastCurrentMax() {
    seedSession("sess_j");
    repo.saveInitialMessages(TENANT, "sess_j", USER, textInputs(ids("j", 2)));
    // A later create carries new message ids — appended past the existing max, never from 1.
    repo.saveInitialMessages(TENANT, "sess_j", USER, textInputs(List.of("j3", "j4")));

    List<ChatMessage> rows = repo.findBySession("sess_j", TENANT);
    assertThat(rows).extracting(ChatMessage::id).containsExactly("j0", "j1", "j3", "j4");
    assertThat(rows).extracting(ChatMessage::sequence).containsExactly(1L, 2L, 3L, 4L);
  }

  @Test
  void saveInitialMessagesSkipsKnownIdsAndKeepsFirstWrite() {
    seedSession("sess_k");
    repo.saveInitialMessages(TENANT, "sess_k", USER, textInputs(ids("k", 2)));
    // Re-create mixes a known id (with changed text) and a new id: known is skipped, new appended.
    repo.saveInitialMessages(
        TENANT,
        "sess_k",
        USER,
        List.of(
            new ChatMessageRepository.InitialMessage(
                "k0", "user", "[{\"type\":\"text\",\"text\":\"changed\"}]", null),
            new ChatMessageRepository.InitialMessage(
                "k2", "user", "[{\"type\":\"text\",\"text\":\"k2\"}]", null)));

    List<ChatMessage> rows = repo.findBySession("sess_k", TENANT);
    assertThat(rows).extracting(ChatMessage::id).containsExactly("k0", "k1", "k2");
    assertThat(rows).extracting(ChatMessage::sequence).containsExactly(1L, 2L, 3L);
    // First-write-wins: k0 keeps its original payload, the "changed" text is ignored.
    assertThat(repo.findById("k0", TENANT, "sess_k").orElseThrow().partsJson()).contains("k0");
  }

  @Test
  void saveInitialMessagesSurvivesConcurrentAppend() throws Exception {
    seedSession("sess_conc");
    int perThread = 4;
    List<String> idsA = ids("ca", perThread);
    List<String> idsB = ids("cb", perThread);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch fire = new CountDownLatch(1);
    AtomicReference<Throwable> error = new AtomicReference<>();

    Thread t1 = new Thread(appendTask("sess_conc", idsA, ready, fire, error));
    Thread t2 = new Thread(appendTask("sess_conc", idsB, ready, fire, error));
    t1.start();
    t2.start();
    assertThat(ready.await(2, TimeUnit.SECONDS)).as("threads reached the start line").isTrue();
    fire.countDown();
    t1.join();
    t2.join();

    assertThat(error.get()).as("concurrent append must not surface a duplicate-key error").isNull();
    List<ChatMessage> rows = repo.findBySession("sess_conc", TENANT);
    assertThat(rows).hasSize(perThread * 2);
    Set<Long> sequences = rows.stream().map(ChatMessage::sequence).collect(Collectors.toSet());
    assertThat(sequences).as("sequences must be unique under contention").hasSize(perThread * 2);
  }

  private Runnable appendTask(
      String session,
      List<String> ids,
      CountDownLatch ready,
      CountDownLatch fire,
      AtomicReference<Throwable> error) {
    return () -> {
      ready.countDown();
      try {
        fire.await();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      try {
        repo.saveInitialMessages(TENANT, session, USER, textInputs(ids));
      } catch (Throwable t) {
        error.compareAndSet(null, t);
      }
    };
  }

  private List<String> ids(String prefix, int n) {
    return IntStream.range(0, n).mapToObj(i -> prefix + i).toList();
  }

  private List<ChatMessageRepository.InitialMessage> textInputs(List<String> ids) {
    return ids.stream()
        .map(
            id ->
                new ChatMessageRepository.InitialMessage(
                    id, "user", "[{\"type\":\"text\",\"text\":\"" + id + "\"}]", null))
        .toList();
  }

  private void seedSession(String id) {
    sessionRepo.save(new ChatSession(id, TENANT, USER, "ch-test", "T", 0L, null, null));
  }

  private ChatMessage message(
      String id, String session, String role, long sequence, String partsJson) {
    return new ChatMessage(id, TENANT, session, USER, role, partsJson, null, sequence, null, null);
  }
}
