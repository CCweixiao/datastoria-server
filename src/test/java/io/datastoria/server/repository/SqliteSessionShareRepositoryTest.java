package io.datastoria.server.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.ActiveProfiles;

import io.datastoria.server.TestDbHelper;
import io.datastoria.server.domain.ChatSession;
import io.datastoria.server.domain.SessionShare;

@SpringBootTest
@ActiveProfiles("test")
class SqliteSessionShareRepositoryTest {

  private static final String TENANT = "tenant-test";
  private static final String USER = "dev@example.com";

  @Autowired ChatSessionRepository sessionRepo;
  @Autowired SessionShareRepository repo;
  @Autowired TestDbHelper dbHelper;

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
  }

  @Test
  void issueAndFindActive() {
    seedSession("sess_a");
    repo.issue(newShare("shr_a", "sess_a", "hash-a", Instant.parse("2100-01-01T00:00:00Z")));

    SessionShare active = repo.findActive("sess_a", TENANT).orElseThrow();
    assertThat(active.tokenHash()).isEqualTo("hash-a");
    assertThat(active.revokedAt()).isNull();
  }

  @Test
  void issuingASecondActiveShareForSameSessionFails() {
    seedSession("sess_b");
    repo.issue(newShare("shr_b1", "sess_b", "hash-b1", Instant.parse("2100-01-01T00:00:00Z")));
    // active_key UNIQUE constraint fires on the second INSERT.
    assertThatThrownBy(
            () ->
                repo.issue(
                    newShare("shr_b2", "sess_b", "hash-b2", Instant.parse("2100-01-01T00:00:00Z"))))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void revokeClearsActiveSlotAndAllowsANewShare() {
    seedSession("sess_c");
    repo.issue(newShare("shr_c1", "sess_c", "hash-c1", Instant.parse("2100-01-01T00:00:00Z")));
    int affected = repo.revoke("sess_c", TENANT);
    assertThat(affected).isEqualTo(1);
    assertThat(repo.findActive("sess_c", TENANT)).isEmpty();

    // After revocation a new active share can be issued.
    repo.issue(newShare("shr_c2", "sess_c", "hash-c2", Instant.parse("2100-01-01T00:00:00Z")));
    SessionShare next = repo.findActive("sess_c", TENANT).orElseThrow();
    assertThat(next.tokenHash()).isEqualTo("hash-c2");
  }

  @Test
  void revokeIsNoOpWhenNoActiveShareExists() {
    seedSession("sess_d");
    assertThat(repo.revoke("sess_d", TENANT)).isZero();
  }

  @Test
  void findByTokenHashResolvesShare() {
    seedSession("sess_e");
    repo.issue(newShare("shr_e", "sess_e", "hash-e", Instant.parse("2100-01-01T00:00:00Z")));
    SessionShare found = repo.findByTokenHash("hash-e").orElseThrow();
    assertThat(found.sessionId()).isEqualTo("sess_e");
    assertThat(found.ownerUserId()).isEqualTo(USER);
  }

  @Test
  void findByTokenHashReturnsEmptyForUnknownHash() {
    seedSession("sess_f");
    repo.issue(newShare("shr_f", "sess_f", "hash-f", Instant.parse("2100-01-01T00:00:00Z")));
    assertThat(repo.findByTokenHash("not-issued")).isEmpty();
  }

  private void seedSession(String id) {
    sessionRepo.save(new ChatSession(id, TENANT, USER, "ch-test", "T", 0L, null, null));
  }

  private SessionShare newShare(String id, String sessionId, String hash, Instant expiresAt) {
    return new SessionShare(id, TENANT, sessionId, USER, hash, expiresAt, null, null);
  }
}
