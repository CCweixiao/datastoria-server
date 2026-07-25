package io.datastoria.server.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import io.datastoria.server.TestDbHelper;
import io.datastoria.server.api.error.NotFoundException;
import io.datastoria.server.domain.ChatSession;
import io.datastoria.server.domain.Ulid;
import io.datastoria.server.repository.jdbc.SessionListCursor;

@SpringBootTest
@ActiveProfiles("test")
class SqliteChatSessionRepositoryTest {

  private static final String TENANT = "tenant-test";
  private static final String USER = "dev@example.com";

  @Autowired ChatSessionRepository repo;
  @Autowired TestDbHelper dbHelper;

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
  }

  @Test
  void saveAndFindByIdRoundTrip() {
    ChatSession saved = repo.save(newSession("sess_a", "ch-test", "Title A"));
    assertThat(saved.revision()).isZero();
    assertThat(saved.createdAt()).isNotNull();
    assertThat(saved.updatedAt()).isNotNull();

    ChatSession found = repo.findById("sess_a", TENANT, USER).orElseThrow();
    assertThat(found.connectionId()).isEqualTo("ch-test");
    assertThat(found.title()).isEqualTo("Title A");
  }

  @Test
  void findByIdExcludesOtherUser() {
    repo.save(newSession("sess_b", "ch-test", "T", "tenant-test", "other@example.com"));
    assertThat(repo.findById("sess_b", TENANT, USER)).isEmpty();
    assertThat(repo.findById("sess_b", TENANT, "other@example.com")).isPresent();
  }

  @Test
  void findPageOrdersByUpdatedAtDescThenIdDesc() throws Exception {
    repo.save(newSession("sess_1", "ch-test", "T1"));
    Thread.sleep(5);
    repo.save(newSession("sess_2", "ch-test", "T2"));
    Thread.sleep(5);
    repo.save(newSession("sess_0", "ch-test", "T0"));

    SessionPage page = repo.findPage(TENANT, USER, null, null, 100);
    assertThat(page.sessions()).hasSize(3);
    assertThat(page.sessions().get(0).id()).isEqualTo("sess_0");
    assertThat(page.sessions().get(1).id()).isEqualTo("sess_2");
    assertThat(page.sessions().get(2).id()).isEqualTo("sess_1");
    assertThat(page.nextCursor()).isNull();
  }

  @Test
  void findPagePaginatesWithOpaqueCursor() throws Exception {
    repo.save(newSession("sess_1", "ch-test", "T1"));
    Thread.sleep(5);
    repo.save(newSession("sess_2", "ch-test", "T2"));
    Thread.sleep(5);
    repo.save(newSession("sess_3", "ch-test", "T3"));

    SessionPage first = repo.findPage(TENANT, USER, null, null, 2);
    assertThat(first.sessions()).hasSize(2);
    assertThat(first.sessions().get(0).id()).isEqualTo("sess_3");
    assertThat(first.sessions().get(1).id()).isEqualTo("sess_2");
    assertThat(first.nextCursor()).isNotNull();

    SessionListCursor cursor = SessionListCursor.parse(first.nextCursor()).orElseThrow();
    SessionPage second = repo.findPage(TENANT, USER, null, cursor, 2);
    assertThat(second.sessions()).hasSize(1);
    assertThat(second.sessions().get(0).id()).isEqualTo("sess_1");
    assertThat(second.nextCursor()).isNull();
  }

  @Test
  void findPageFiltersByConnectionId() throws Exception {
    repo.save(newSession("sess_a", "ch-prod", "A"));
    repo.save(newSession("sess_b", "ch-test", "B"));
    repo.save(newSession("sess_c", "ch-test", "C"));

    SessionPage page = repo.findPage(TENANT, USER, "ch-test", null, 100);
    assertThat(page.sessions()).hasSize(2);
    assertThat(page.sessions()).allSatisfy(s -> assertThat(s.connectionId()).isEqualTo("ch-test"));
  }

  @Test
  void renameUpdatesTitleAndBumpsRevision() {
    repo.save(newSession("sess_r", "ch-test", "Old"));
    ChatSession renamed = repo.rename("sess_r", TENANT, USER, "New");
    assertThat(renamed.title()).isEqualTo("New");
    assertThat(renamed.revision()).isEqualTo(1L);
  }

  @Test
  void renameThrowsNotFoundForMissingOrCrossTenant() {
    assertThatThrownBy(() -> repo.rename("nope", TENANT, USER, "X"))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void deleteRemovesRow() {
    repo.save(newSession("sess_d", "ch-test", "D"));
    repo.delete("sess_d", TENANT, USER);
    assertThat(repo.findById("sess_d", TENANT, USER)).isEmpty();
  }

  @Test
  void deleteThrowsNotFoundForMissing() {
    assertThatThrownBy(() -> repo.delete("nope", TENANT, USER))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void findAllByConnectionReturnsAllMatchesOrdered() throws Exception {
    repo.save(newSession("a", "ch-x", "A"));
    Thread.sleep(5);
    repo.save(newSession("b", "ch-x", "B"));
    repo.save(newSession("c", "ch-y", "C"));

    var rows = repo.findAllByConnection(TENANT, USER, "ch-x");
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).id()).isEqualTo("b");
    assertThat(rows.get(1).id()).isEqualTo("a");
  }

  private ChatSession newSession(String id, String conn, String title) {
    return newSession(id, conn, title, TENANT, USER);
  }

  private ChatSession newSession(String id, String conn, String title, String tenant, String user) {
    // ID reserved for fixtures; production callers go through Ulid.next().
    Optional.ofNullable(id).orElse(Ulid.next());
    return new ChatSession(id, tenant, user, conn, title, 0L, null, null);
  }
}
