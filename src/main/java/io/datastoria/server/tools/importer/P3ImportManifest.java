package io.datastoria.server.tools.importer;

import java.time.Instant;
import java.util.Map;

/**
 * Manifest describing the expected contents of a JSONL bundle.
 *
 * <p>The manifest sits at the root of an import directory next to {@code sessions.jsonl} / {@code
 * messages.jsonl} / {@code feedback.jsonl} / {@code shares.jsonl}. The importer uses it both to
 * size progress and to verify the post-import row counts. Rows are counted by parsing every line,
 * so the manifest is a checksum, not a primary source of truth.
 *
 * <p>Version is currently {@code 1}. Future schema changes must bump the version and document a
 * import path in {@code docs/reference/import-bundle-format.md}.
 */
public record P3ImportManifest(
    int version,
    Instant generatedAt,
    String sourceDialect,
    Map<String, Long> expectedRowCounts,
    Map<String, Long> expectedTenantCounts) {

  /** Table names used in the manifest's {@code expectedRowCounts} map. */
  public static final String SESSIONS = "sessions";

  public static final String MESSAGES = "messages";
  public static final String FEEDBACK = "feedback";
  public static final String SHARES = "shares";

  /** Current manifest schema version. */
  public static final int CURRENT_VERSION = 1;
}
