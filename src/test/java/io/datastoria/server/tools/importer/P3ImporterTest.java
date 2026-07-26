package io.datastoria.server.tools.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.datastoria.server.TestDbHelper;
import io.datastoria.server.persistence.mapper.P3ImportMapper;
import io.datastoria.server.tools.importer.ImportRows.FeedbackRow;
import io.datastoria.server.tools.importer.ImportRows.MessageRow;
import io.datastoria.server.tools.importer.ImportRows.SessionRow;
import io.datastoria.server.tools.importer.ImportRows.ShareRow;

/**
 * Integration tests for {@link P3Importer}. Each test writes a JSONL bundle to a temp dir, runs the
 * importer against the test SQLite context, and verifies the persisted rows + checksum.
 *
 * <p>Covers the P3 acceptance criterion "导入重复执行" (idempotent import): running the same bundle twice
 * must yield identical row counts and identical checksums, with no duplicate primary keys.
 */
@SpringBootTest
@ActiveProfiles("test")
class P3ImporterTest {

  @Autowired private JdbcClient jdbc;
  @Autowired private ObjectMapper mapper;
  @Autowired private TestDbHelper dbHelper;
  @Autowired private P3ImportMapper p3Mapper;
  @Autowired private org.springframework.transaction.support.TransactionTemplate transactions;

  private P3Importer importer;

  @TempDir Path tmp;

  @BeforeEach
  void setUp() {
    dbHelper.cleanAll();
    importer = new P3Importer(p3Mapper, transactions, mapper);
  }

  @Test
  @DisplayName("happyPath: imports sessions, messages, feedback and shares")
  void happyPath() throws IOException {
    Path dir = newBundle();
    P3ImportResult result = importer.run(dir, false);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.totalInserted()).isEqualTo(7L);
    assertThat(result.totalUpdated()).isZero();
    assertThat(result.totalErrors()).isZero();
    assertThat(result.checksum().matches()).isTrue();

    assertThat(countSessions()).isEqualTo(2);
    assertThat(countMessages()).isEqualTo(2);
    assertThat(countFeedback()).isEqualTo(2);
    assertThat(countShares()).isEqualTo(1);
  }

  @Test
  @DisplayName("idempotency: importing the same bundle twice yields no new rows")
  void idempotency() throws IOException {
    Path dir = newBundle();
    importer.run(dir, false);
    P3ImportResult second = importer.run(dir, false);

    assertThat(second.isSuccess()).isTrue();
    assertThat(second.totalInserted()).isZero();
    assertThat(second.totalUpdated()).isEqualTo(7L);
    assertThat(second.checksum().matches()).isTrue();

    assertThat(countSessions()).isEqualTo(2);
    assertThat(countMessages()).isEqualTo(2);
    assertThat(countFeedback()).isEqualTo(2);
    assertThat(countShares()).isEqualTo(1);
  }

  @Test
  @DisplayName("dryRun: parses and counts but leaves the database empty")
  void dryRun() throws IOException {
    Path dir = newBundle();
    P3ImportResult result = importer.run(dir, true);

    assertThat(result.dryRun()).isTrue();
    assertThat(result.checksum().matches()).isTrue();
    assertThat(countSessions()).isZero();
    assertThat(countMessages()).isZero();
    assertThat(countFeedback()).isZero();
    assertThat(countShares()).isZero();
  }

  @Test
  @DisplayName("manifestMismatch: extra row in sessions.jsonl flips checksum to false")
  void manifestMismatch() throws IOException {
    Path dir = newBundle();
    // Append an extra session row the manifest doesn't account for.
    Path sessions = dir.resolve("sessions.jsonl");
    SessionRow extra =
        new SessionRow(
            "sess-extra",
            "tenant-a",
            "alice",
            "conn-1",
            "Extra",
            0L,
            Instant.parse("2026-07-25T00:00:00Z"),
            Instant.parse("2026-07-25T00:00:00Z"));
    Files.writeString(
        sessions, mapper.writeValueAsString(extra) + "\n", java.nio.file.StandardOpenOption.APPEND);

    P3ImportResult result = importer.run(dir, false);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.checksum().matches()).isFalse();
    assertThat(result.checksum().expected().get(P3ImportManifest.SESSIONS)).isEqualTo(2L);
    assertThat(result.checksum().actual().get(P3ImportManifest.SESSIONS)).isEqualTo(3L);
  }

  @Test
  @DisplayName("missingManifest: throws IOException when manifest.json is absent")
  void missingManifest() {
    Path dir = tmp.resolve("no-manifest");
    assertThat(dir.toFile().mkdirs()).isTrue();
    org.assertj.core.api.Assertions.assertThatIOException()
        .isThrownBy(() -> importer.run(dir, false))
        .withMessageContaining("manifest.json");
  }

  @Test
  @DisplayName("crossTenant: rows for tenant-a do not leak into tenant-b queries")
  void crossTenant() throws IOException {
    Path dir = newBundle();
    importer.run(dir, false);
    // Re-import a tenant-b bundle into the same DB; tenant-a rows must remain untouched.
    Path dirB =
        writeBundle(
            "tenant-b",
            List.of(
                new SessionRow(
                    "sess-b1",
                    "tenant-b",
                    "bob",
                    "conn-b",
                    "B",
                    0L,
                    Instant.parse("2026-07-25T00:00:00Z"),
                    Instant.parse("2026-07-25T00:00:00Z"))),
            List.of(),
            List.of(),
            List.of(),
            Map.of(
                P3ImportManifest.SESSIONS, 1L,
                P3ImportManifest.MESSAGES, 0L,
                P3ImportManifest.FEEDBACK, 0L,
                P3ImportManifest.SHARES, 0L));
    importer.run(dirB, false);

    assertThat(countSessionsForTenant("tenant-a")).isEqualTo(2);
    assertThat(countSessionsForTenant("tenant-b")).isEqualTo(1);
  }

  @Test
  @DisplayName("invalidJson: parser reports one error per broken line and skips the row")
  void invalidJson() throws IOException {
    Path dir = newBundle();
    Files.writeString(
        dir.resolve("sessions.jsonl"),
        "{ this is not json\n",
        java.nio.file.StandardOpenOption.APPEND);
    P3ImportResult result = importer.run(dir, false);

    assertThat(result.totalErrors()).isEqualTo(1);
    assertThat(result.errors().get(0).file()).isEqualTo("sessions.jsonl");
    assertThat(result.errors().get(0).line()).isGreaterThan(0L);
  }

  @Test
  @DisplayName("missingRequiredField: row without id is reported and skipped")
  void missingRequiredField() throws IOException {
    Path dir = newBundle();
    Files.writeString(
        dir.resolve("messages.jsonl"),
        "{\"tenantId\":\"tenant-a\",\"sessionId\":\"sess-1\",\"userId\":\"alice\","
            + "\"role\":\"user\",\"partsJson\":\"[]\",\"sequence\":9,"
            + "\"createdAt\":\"2026-07-25T00:00:00Z\",\"updatedAt\":\"2026-07-25T00:00:00Z\"}\n",
        java.nio.file.StandardOpenOption.APPEND);
    P3ImportResult result = importer.run(dir, false);

    assertThat(result.totalErrors()).isEqualTo(1);
    assertThat(result.errors().get(0).file()).isEqualTo("messages.jsonl");
    assertThat(result.errors().get(0).message()).contains("missing required field");
  }

  @Test
  @DisplayName("missingFile: absent jsonl files are skipped silently (manifest drives counts)")
  void missingFile() throws IOException {
    Path dir =
        writeBundle(
            "tenant-a",
            List.of(
                new SessionRow(
                    "sess-solo",
                    "tenant-a",
                    "alice",
                    "conn-1",
                    "Solo",
                    0L,
                    Instant.parse("2026-07-25T00:00:00Z"),
                    Instant.parse("2026-07-25T00:00:00Z"))),
            null, // no messages.jsonl
            null, // no feedback.jsonl
            null, // no shares.jsonl
            Map.of(
                P3ImportManifest.SESSIONS, 1L,
                P3ImportManifest.MESSAGES, 0L,
                P3ImportManifest.FEEDBACK, 0L,
                P3ImportManifest.SHARES, 0L));
    P3ImportResult result = importer.run(dir, false);

    assertThat(result.isSuccess()).isTrue();
    assertThat(countSessions()).isEqualTo(1);
  }

  // ----------------------------- helpers -----------------------------

  /** Builds the canonical 7-row bundle used by happyPath / idempotency / dryRun. */
  private Path newBundle() throws IOException {
    Instant t0 = Instant.parse("2026-07-25T00:00:00Z");
    List<SessionRow> sessions =
        List.of(
            new SessionRow("sess-1", "tenant-a", "alice", "conn-1", "Session 1", 0L, t0, t0),
            new SessionRow("sess-2", "tenant-a", "alice", "conn-1", "Session 2", 0L, t0, t0));
    List<MessageRow> messages =
        List.of(
            new MessageRow(
                "msg-1",
                "tenant-a",
                "sess-1",
                "alice",
                "user",
                "[{\"type\":\"text\",\"text\":\"hi\"}]",
                null,
                1L,
                t0,
                t0),
            new MessageRow(
                "msg-2",
                "tenant-a",
                "sess-1",
                "alice",
                "assistant",
                "[{\"type\":\"text\",\"text\":\"hello\"}]",
                null,
                2L,
                t0,
                t0));
    List<FeedbackRow> feedback =
        List.of(
            new FeedbackRow(
                "fb-1",
                "tenant-a",
                "alice",
                "auto_explain_error",
                "sess-1",
                "msg-1",
                true,
                null,
                "{\"queryId\":\"q1\"}",
                null,
                false,
                t0,
                t0),
            new FeedbackRow(
                "fb-2",
                "tenant-a",
                "alice",
                "auto_explain_error",
                "sess-2",
                "msg-2",
                false,
                "too_vague",
                "{\"queryId\":\"q2\"}",
                "needs more detail",
                false,
                t0,
                t0));
    List<ShareRow> shares =
        List.of(
            new ShareRow(
                "share-1",
                "tenant-a",
                "sess-1",
                "alice",
                "deadbeef".repeat(8),
                Instant.parse("2100-01-01T00:00:00Z"),
                null,
                t0));
    Map<String, Long> counts =
        Map.of(
            P3ImportManifest.SESSIONS, 2L,
            P3ImportManifest.MESSAGES, 2L,
            P3ImportManifest.FEEDBACK, 2L,
            P3ImportManifest.SHARES, 1L);
    return writeBundle("tenant-a", sessions, messages, feedback, shares, counts);
  }

  private Path writeBundle(
      String tenant,
      List<SessionRow> sessions,
      List<MessageRow> messages,
      List<FeedbackRow> feedback,
      List<ShareRow> shares,
      Map<String, Long> counts)
      throws IOException {
    Path dir = tmp.resolve("bundle-" + tenant + "-" + System.nanoTime());
    assertThat(dir.toFile().mkdirs()).isTrue();

    // Manifest uses an ordered map so the keys are stable on disk.
    Map<String, Long> orderedCounts = new LinkedHashMap<>();
    orderedCounts.put(
        P3ImportManifest.SESSIONS, counts.getOrDefault(P3ImportManifest.SESSIONS, 0L));
    orderedCounts.put(
        P3ImportManifest.MESSAGES, counts.getOrDefault(P3ImportManifest.MESSAGES, 0L));
    orderedCounts.put(
        P3ImportManifest.FEEDBACK, counts.getOrDefault(P3ImportManifest.FEEDBACK, 0L));
    orderedCounts.put(P3ImportManifest.SHARES, counts.getOrDefault(P3ImportManifest.SHARES, 0L));

    P3ImportManifest manifest =
        new P3ImportManifest(
            P3ImportManifest.CURRENT_VERSION,
            Instant.parse("2026-07-25T00:00:00Z"),
            "sqlite",
            orderedCounts,
            Map.of(tenant, (long) sessions.size()));
    Files.writeString(dir.resolve("manifest.json"), mapper.writeValueAsString(manifest) + "\n");

    if (sessions != null) {
      Jsonl.writeLines(dir.resolve("sessions.jsonl"), sessions, mapper);
    }
    if (messages != null) {
      Jsonl.writeLines(dir.resolve("messages.jsonl"), messages, mapper);
    }
    if (feedback != null) {
      Jsonl.writeLines(dir.resolve("feedback.jsonl"), feedback, mapper);
    }
    if (shares != null) {
      Jsonl.writeLines(dir.resolve("shares.jsonl"), shares, mapper);
    }
    return dir;
  }

  private long countSessions() {
    return jdbc.sql("SELECT COUNT(*) FROM ds_chat_session").query(Long.class).single();
  }

  private long countSessionsForTenant(String tenantId) {
    return jdbc.sql("SELECT COUNT(*) FROM ds_chat_session WHERE tenant_id = :t")
        .param("t", tenantId)
        .query(Long.class)
        .single();
  }

  private long countMessages() {
    return jdbc.sql("SELECT COUNT(*) FROM ds_chat_message").query(Long.class).single();
  }

  private long countFeedback() {
    return jdbc.sql("SELECT COUNT(*) FROM ds_feedback_event").query(Long.class).single();
  }

  private long countShares() {
    return jdbc.sql("SELECT COUNT(*) FROM ds_session_share").query(Long.class).single();
  }
}
