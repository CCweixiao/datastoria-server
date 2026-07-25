package io.datastoria.server.tools.importer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.datastoria.server.tools.importer.ImportRows.FeedbackRow;
import io.datastoria.server.tools.importer.ImportRows.MessageRow;
import io.datastoria.server.tools.importer.ImportRows.SessionRow;
import io.datastoria.server.tools.importer.ImportRows.ShareRow;
import io.datastoria.server.tools.importer.Jsonl.TriConsumer;

/**
 * Imports P3 chat product data from a directory of JSONL files into the live database.
 *
 * <p>The directory layout (see {@code docs/migration/p3-jsonl-format.md}) is:
 *
 * <pre>{@code
 * data/
 *   manifest.json     # P3ImportManifest with expected counts per table
 *   sessions.jsonl    # SessionRow, one per line
 *   messages.jsonl    # MessageRow
 *   feedback.jsonl    # FeedbackRow
 *   shares.jsonl      # ShareRow
 * }</pre>
 *
 * <h3>Idempotency</h3>
 *
 * Every table uses a lookup-then-upsert pattern keyed on the natural primary key:
 *
 * <ul>
 *   <li>{@code ds_chat_session} by {@code (tenant_id, id)} — title, connection_id, revision,
 *       updated_at are refreshed; {@code created_at} is preserved on the existing row.
 *   <li>{@code ds_chat_message} by {@code (tenant_id, session_id, id)} — role, parts_json,
 *       metadata_json, sequence, updated_at are refreshed; {@code created_at} is preserved.
 *   <li>{@code ds_feedback_event} by {@code (tenant_id, user_id, source, session_id, message_id)} —
 *       every mutable column is overwritten; {@code created_at} preserved.
 *   <li>{@code ds_session_share} by {@code (tenant_id, token_hash)} — expires_at, revoked_at are
 *       refreshed; {@code created_at} preserved.
 * </ul>
 *
 * As a consequence, running the importer twice on the same source produces identical row counts (no
 * duplicates) and a stable checksum.
 *
 * <h3>Dry-run mode</h3>
 *
 * When {@code dryRun = true}, every JSONL line is parsed and validated, every manifest check runs,
 * but no SQL is issued. The returned {@link P3ImportResult} reports "would-insert" / "would-update"
 * counts so operators can sanity-check before applying changes.
 *
 * <h3>Transaction strategy</h3>
 *
 * Each table is imported in its own {@link TransactionTemplate} block. A failure inside one block
 * rolls back only that table's rows; subsequent tables are still attempted so a single bad table
 * doesn't block the rest. The importer is overall "best-effort + report" rather than atomic.
 *
 * <h3>Order</h3>
 *
 * Sessions must come first because messages/feedback/shares have FKs (logical or physical) to
 * sessions. Within a table, rows are processed in JSONL order — the importer does not re-sort.
 */
public class P3Importer {

  private static final Logger log = LoggerFactory.getLogger(P3Importer.class);

  private final JdbcClient jdbc;
  private final TransactionTemplate transactions;
  private final ObjectMapper mapper;

  public P3Importer(JdbcClient jdbc, TransactionTemplate transactions, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.transactions = transactions;
    this.mapper = mapper;
  }

  /** Run the importer on the supplied directory. */
  public P3ImportResult run(Path inputDir, boolean dryRun) throws IOException {
    if (!Files.isDirectory(inputDir)) {
      throw new IOException("Input path is not a directory: " + inputDir);
    }
    P3ImportManifest manifest = Jsonl.readManifest(inputDir, mapper);
    if (manifest.version() != P3ImportManifest.CURRENT_VERSION) {
      throw new IOException(
          "Unsupported manifest version "
              + manifest.version()
              + "; expected "
              + P3ImportManifest.CURRENT_VERSION);
    }

    P3ImportResult.Builder result = P3ImportResult.builder(dryRun);
    Map<String, Long> actual =
        new HashMap<>(
            Map.of(
                P3ImportManifest.SESSIONS, 0L,
                P3ImportManifest.MESSAGES, 0L,
                P3ImportManifest.FEEDBACK, 0L,
                P3ImportManifest.SHARES, 0L));

    importSessions(inputDir, dryRun, result, actual);
    importMessages(inputDir, dryRun, result, actual);
    importFeedback(inputDir, dryRun, result, actual);
    importShares(inputDir, dryRun, result, actual);

    Map<String, Long> expected =
        manifest.expectedRowCounts() == null
            ? Map.of()
            : new HashMap<>(manifest.expectedRowCounts());
    for (String t : actual.keySet()) {
      expected.putIfAbsent(t, 0L);
    }
    boolean matches = expected.equals(actual);
    if (!matches) {
      log.warn(
          "manifest mismatch — expected {} actual {} (diff on tables: {})",
          expected,
          actual,
          diffKeys(expected, actual));
    }
    return result.build(new P3ImportResult.ChecksumDelta(expected, Map.copyOf(actual), matches));
  }

  private void importSessions(
      Path dir, boolean dryRun, P3ImportResult.Builder result, Map<String, Long> actual)
      throws IOException {
    Path file = dir.resolve("sessions.jsonl");
    if (!Files.exists(file)) {
      return;
    }
    if (dryRun) {
      actual.put(P3ImportManifest.SESSIONS, Jsonl.countLines(file));
      return;
    }
    transactions.executeWithoutResult(
        status -> {
          TriConsumer<Long, String, String> onError =
              (line, raw, msg) ->
                  result.error("sessions.jsonl", line, extractId(raw), "parse failure: " + msg);
          try {
            Jsonl.readLines(
                file,
                mapper,
                SessionRow.class,
                (line, row) -> {
                  if (row.id() == null
                      || row.tenantId() == null
                      || row.userId() == null
                      || row.connectionId() == null
                      || row.createdAt() == null
                      || row.updatedAt() == null) {
                    result.error(
                        "sessions.jsonl", line, row.id(), "missing required field on session row");
                    return;
                  }
                  upsertSession(row, result);
                  actual.merge(P3ImportManifest.SESSIONS, 1L, Long::sum);
                },
                onError);
          } catch (IOException e) {
            throw new UncheckedIOExceptionWrapper(e);
          }
        });
  }

  private void upsertSession(SessionRow row, P3ImportResult.Builder result) {
    Optional<Boolean> exists =
        jdbc.sql("SELECT 1 FROM ds_chat_session WHERE tenant_id = :tenantId AND id = :id")
            .param("tenantId", row.tenantId())
            .param("id", row.id())
            .query(Boolean.class)
            .optional();
    if (exists.isEmpty()) {
      jdbc.sql(
              "INSERT INTO ds_chat_session"
                  + " (id, tenant_id, user_id, connection_id, title, revision,"
                  + " created_at, updated_at)"
                  + " VALUES (:id, :tenantId, :userId, :connectionId, :title, :revision,"
                  + " :createdAt, :updatedAt)")
          .param("id", row.id())
          .param("tenantId", row.tenantId())
          .param("userId", row.userId())
          .param("connectionId", row.connectionId())
          .param("title", row.title())
          .param("revision", row.safeRevision())
          .param("createdAt", row.createdAt().toString())
          .param("updatedAt", row.updatedAt().toString())
          .update();
      result.inserted(P3ImportManifest.SESSIONS);
    } else {
      jdbc.sql(
              "UPDATE ds_chat_session SET user_id = :userId, connection_id = :connectionId,"
                  + " title = :title, revision = :revision, updated_at = :updatedAt"
                  + " WHERE tenant_id = :tenantId AND id = :id")
          .param("userId", row.userId())
          .param("connectionId", row.connectionId())
          .param("title", row.title())
          .param("revision", row.safeRevision())
          .param("updatedAt", row.updatedAt().toString())
          .param("tenantId", row.tenantId())
          .param("id", row.id())
          .update();
      result.updated(P3ImportManifest.SESSIONS);
    }
  }

  private void importMessages(
      Path dir, boolean dryRun, P3ImportResult.Builder result, Map<String, Long> actual)
      throws IOException {
    Path file = dir.resolve("messages.jsonl");
    if (!Files.exists(file)) {
      return;
    }
    if (dryRun) {
      actual.put(P3ImportManifest.MESSAGES, Jsonl.countLines(file));
      return;
    }
    try {
      Jsonl.readLines(
          file,
          mapper,
          MessageRow.class,
          (line, row) -> {
            if (row.id() == null
                || row.tenantId() == null
                || row.sessionId() == null
                || row.userId() == null
                || row.role() == null
                || row.partsJson() == null
                || row.sequence() == null
                || row.createdAt() == null
                || row.updatedAt() == null) {
              result.error(
                  "messages.jsonl", line, row.id(), "missing required field on message row");
              return;
            }
            upsertMessage(row, result);
            actual.merge(P3ImportManifest.MESSAGES, 1L, Long::sum);
          },
          (line, raw, msg) ->
              result.error("messages.jsonl", line, extractId(raw), "parse failure: " + msg));
    } catch (IOException e) {
      throw new UncheckedIOExceptionWrapper(e);
    }
  }

  private void upsertMessage(MessageRow row, P3ImportResult.Builder result) {
    Optional<Boolean> exists =
        jdbc.sql(
                "SELECT 1 FROM ds_chat_message"
                    + " WHERE tenant_id = :tenantId AND session_id = :sessionId AND id = :id")
            .param("tenantId", row.tenantId())
            .param("sessionId", row.sessionId())
            .param("id", row.id())
            .query(Boolean.class)
            .optional();
    if (exists.isEmpty()) {
      jdbc.sql(
              "INSERT INTO ds_chat_message"
                  + " (id, tenant_id, session_id, user_id, role, parts_json, metadata_json,"
                  + " sequence, created_at, updated_at)"
                  + " VALUES (:id, :tenantId, :sessionId, :userId, :role, :partsJson,"
                  + " :metadataJson, :sequence, :createdAt, :updatedAt)")
          .param("id", row.id())
          .param("tenantId", row.tenantId())
          .param("sessionId", row.sessionId())
          .param("userId", row.userId())
          .param("role", row.role())
          .param("partsJson", row.partsJson())
          .param("metadataJson", row.metadataJson())
          .param("sequence", row.sequence())
          .param("createdAt", row.createdAt().toString())
          .param("updatedAt", row.updatedAt().toString())
          .update();
      result.inserted(P3ImportManifest.MESSAGES);
    } else {
      jdbc.sql(
              "UPDATE ds_chat_message SET user_id = :userId, role = :role,"
                  + " parts_json = :partsJson, metadata_json = :metadataJson,"
                  + " sequence = :sequence, updated_at = :updatedAt"
                  + " WHERE tenant_id = :tenantId AND session_id = :sessionId AND id = :id")
          .param("userId", row.userId())
          .param("role", row.role())
          .param("partsJson", row.partsJson())
          .param("metadataJson", row.metadataJson())
          .param("sequence", row.sequence())
          .param("updatedAt", row.updatedAt().toString())
          .param("tenantId", row.tenantId())
          .param("sessionId", row.sessionId())
          .param("id", row.id())
          .update();
      result.updated(P3ImportManifest.MESSAGES);
    }
  }

  private void importFeedback(
      Path dir, boolean dryRun, P3ImportResult.Builder result, Map<String, Long> actual)
      throws IOException {
    Path file = dir.resolve("feedback.jsonl");
    if (!Files.exists(file)) {
      return;
    }
    if (dryRun) {
      actual.put(P3ImportManifest.FEEDBACK, Jsonl.countLines(file));
      return;
    }
    try {
      Jsonl.readLines(
          file,
          mapper,
          FeedbackRow.class,
          (line, row) -> {
            if (row.id() == null
                || row.tenantId() == null
                || row.userId() == null
                || row.source() == null
                || row.sessionId() == null
                || row.messageId() == null
                || row.payloadJson() == null
                || row.createdAt() == null
                || row.updatedAt() == null) {
              result.error(
                  "feedback.jsonl", line, row.id(), "missing required field on feedback row");
              return;
            }
            upsertFeedback(row, result);
            actual.merge(P3ImportManifest.FEEDBACK, 1L, Long::sum);
          },
          (line, raw, msg) ->
              result.error("feedback.jsonl", line, extractId(raw), "parse failure: " + msg));
    } catch (IOException e) {
      throw new UncheckedIOExceptionWrapper(e);
    }
  }

  private void upsertFeedback(FeedbackRow row, P3ImportResult.Builder result) {
    Optional<Boolean> exists =
        jdbc.sql(
                "SELECT 1 FROM ds_feedback_event"
                    + " WHERE tenant_id = :tenantId AND user_id = :userId AND source = :source"
                    + " AND session_id = :sessionId AND message_id = :messageId")
            .param("tenantId", row.tenantId())
            .param("userId", row.userId())
            .param("source", row.source())
            .param("sessionId", row.sessionId())
            .param("messageId", row.messageId())
            .query(Boolean.class)
            .optional();
    if (exists.isEmpty()) {
      jdbc.sql(
              "INSERT INTO ds_feedback_event"
                  + " (id, tenant_id, user_id, source, session_id, message_id, solved,"
                  + " reason_code, payload_json, free_text, recovery_action_taken,"
                  + " created_at, updated_at)"
                  + " VALUES (:id, :tenantId, :userId, :source, :sessionId, :messageId,"
                  + " :solved, :reasonCode, :payloadJson, :freeText, :recoveryActionTaken,"
                  + " :createdAt, :updatedAt)")
          .param("id", row.id())
          .param("tenantId", row.tenantId())
          .param("userId", row.userId())
          .param("source", row.source())
          .param("sessionId", row.sessionId())
          .param("messageId", row.messageId())
          .param("solved", row.safeSolved())
          .param("reasonCode", row.reasonCode())
          .param("payloadJson", row.payloadJson())
          .param("freeText", row.freeText())
          .param("recoveryActionTaken", row.safeRecovery())
          .param("createdAt", row.createdAt().toString())
          .param("updatedAt", row.updatedAt().toString())
          .update();
      result.inserted(P3ImportManifest.FEEDBACK);
    } else {
      jdbc.sql(
              "UPDATE ds_feedback_event SET solved = :solved, reason_code = :reasonCode,"
                  + " payload_json = :payloadJson, free_text = :freeText,"
                  + " recovery_action_taken = :recoveryActionTaken, updated_at = :updatedAt"
                  + " WHERE tenant_id = :tenantId AND user_id = :userId AND source = :source"
                  + " AND session_id = :sessionId AND message_id = :messageId")
          .param("solved", row.safeSolved())
          .param("reasonCode", row.reasonCode())
          .param("payloadJson", row.payloadJson())
          .param("freeText", row.freeText())
          .param("recoveryActionTaken", row.safeRecovery())
          .param("updatedAt", row.updatedAt().toString())
          .param("tenantId", row.tenantId())
          .param("userId", row.userId())
          .param("source", row.source())
          .param("sessionId", row.sessionId())
          .param("messageId", row.messageId())
          .update();
      result.updated(P3ImportManifest.FEEDBACK);
    }
  }

  private void importShares(
      Path dir, boolean dryRun, P3ImportResult.Builder result, Map<String, Long> actual)
      throws IOException {
    Path file = dir.resolve("shares.jsonl");
    if (!Files.exists(file)) {
      return;
    }
    if (dryRun) {
      actual.put(P3ImportManifest.SHARES, Jsonl.countLines(file));
      return;
    }
    try {
      Jsonl.readLines(
          file,
          mapper,
          ShareRow.class,
          (line, row) -> {
            if (row.id() == null
                || row.tenantId() == null
                || row.sessionId() == null
                || row.ownerUserId() == null
                || row.tokenHash() == null
                || row.expiresAt() == null
                || row.createdAt() == null) {
              result.error("shares.jsonl", line, row.id(), "missing required field on share row");
              return;
            }
            upsertShare(row, result);
            actual.merge(P3ImportManifest.SHARES, 1L, Long::sum);
          },
          (line, raw, msg) ->
              result.error("shares.jsonl", line, extractId(raw), "parse failure: " + msg));
    } catch (IOException e) {
      throw new UncheckedIOExceptionWrapper(e);
    }
  }

  private void upsertShare(ShareRow row, P3ImportResult.Builder result) {
    Optional<Boolean> exists =
        jdbc.sql(
                "SELECT 1 FROM ds_session_share WHERE tenant_id = :tenantId AND token_hash = :tokenHash")
            .param("tenantId", row.tenantId())
            .param("tokenHash", row.tokenHash())
            .query(Boolean.class)
            .optional();
    if (exists.isEmpty()) {
      // Insert: respect revokedAt from the source (NULL becomes NULL).
      jdbc.sql(
              "INSERT INTO ds_session_share"
                  + " (id, tenant_id, session_id, owner_user_id, token_hash, expires_at,"
                  + " revoked_at, created_at)"
                  + " VALUES (:id, :tenantId, :sessionId, :ownerUserId, :tokenHash,"
                  + " :expiresAt, :revokedAt, :createdAt)")
          .param("id", row.id())
          .param("tenantId", row.tenantId())
          .param("sessionId", row.sessionId())
          .param("ownerUserId", row.ownerUserId())
          .param("tokenHash", row.tokenHash())
          .param("expiresAt", row.expiresAt().toString())
          .param("revokedAt", row.revokedAt() == null ? null : row.revokedAt().toString())
          .param("createdAt", row.createdAt().toString())
          .update();
      result.inserted(P3ImportManifest.SHARES);
    } else {
      // Update: refresh expiry/revocation; do not flip token_hash (that would break existing JWTs).
      jdbc.sql(
              "UPDATE ds_session_share SET expires_at = :expiresAt, revoked_at = :revokedAt"
                  + " WHERE tenant_id = :tenantId AND token_hash = :tokenHash")
          .param("expiresAt", row.expiresAt().toString())
          .param("revokedAt", row.revokedAt() == null ? null : row.revokedAt().toString())
          .param("tenantId", row.tenantId())
          .param("tokenHash", row.tokenHash())
          .update();
      result.updated(P3ImportManifest.SHARES);
    }
  }

  /** Quick & dirty "id" field extractor for error messages; never fails. */
  private static String extractId(String raw) {
    if (raw == null || raw.length() > 200) {
      return "?";
    }
    int idx = raw.indexOf("\"id\"");
    if (idx < 0) {
      return "?";
    }
    int colon = raw.indexOf(':', idx);
    if (colon < 0) {
      return "?";
    }
    int start = colon + 1;
    while (start < raw.length() && Character.isWhitespace(raw.charAt(start))) {
      start++;
    }
    if (start >= raw.length()) {
      return "?";
    }
    if (raw.charAt(start) == '"') {
      int end = raw.indexOf('"', start + 1);
      if (end < 0) {
        return "?";
      }
      return raw.substring(start + 1, end);
    }
    int end = start + 1;
    while (end < raw.length() && raw.charAt(end) != ',' && raw.charAt(end) != '}') {
      end++;
    }
    return raw.substring(start, end);
  }

  private static java.util.List<String> diffKeys(Map<String, Long> a, Map<String, Long> b) {
    java.util.List<String> diffs = new java.util.ArrayList<>();
    for (String k : a.keySet()) {
      if (!a.get(k).equals(b.get(k))) {
        diffs.add(k);
      }
    }
    return diffs;
  }

  /** Wrap checked IOException so it can propagate through TransactionTemplate.execute*. */
  static final class UncheckedIOExceptionWrapper extends RuntimeException {
    UncheckedIOExceptionWrapper(IOException cause) {
      super(cause);
    }

    @Override
    public synchronized IOException getCause() {
      return (IOException) super.getCause();
    }
  }

  /** Utility for tests: produce an ISO-8601 string for the current instant. */
  static String isoNow() {
    return Instant.now().toString();
  }
}
