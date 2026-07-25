package io.datastoria.server.tools.importer;

import java.time.Instant;

/**
 * Row DTOs matching the JSONL wire format. Field names are camelCase so the importer accepts
 * exports from either Java or Node.js without translation. See {@code
 * docs/migration/p3-jsonl-format.md} for the schema.
 *
 * <p>Timestamps accept ISO-8601 strings (e.g. {@code "2026-07-25T10:15:30Z"}); Jackson's
 * JavaTimeModule (configured globally on the application {@link
 * com.fasterxml.jackson.databind.ObjectMapper ObjectMapper}) handles the round-trip.
 *
 * <p>All four records are intentionally flat — they mirror {@code ds_chat_session}, {@code
 * ds_chat_message}, {@code ds_feedback_event} and {@code ds_session_share} 1:1 so the importer can
 * move straight to SQL without further transformation.
 */
public final class ImportRows {

  private ImportRows() {}

  /** Mirrors {@code ds_chat_session}. {@code revision} defaults to 0 if absent. */
  public record SessionRow(
      String id,
      String tenantId,
      String userId,
      String connectionId,
      String title,
      Long revision,
      Instant createdAt,
      Instant updatedAt) {

    public long safeRevision() {
      return revision == null ? 0L : revision;
    }
  }

  /** Mirrors {@code ds_chat_message}. */
  public record MessageRow(
      String id,
      String tenantId,
      String sessionId,
      String userId,
      String role,
      String partsJson,
      String metadataJson,
      Long sequence,
      Instant createdAt,
      Instant updatedAt) {

    public long safeSequence() {
      return sequence == null ? 0L : sequence;
    }
  }

  /** Mirrors {@code ds_feedback_event}. {@code source} must equal {@code auto_explain_error}. */
  public record FeedbackRow(
      String id,
      String tenantId,
      String userId,
      String source,
      String sessionId,
      String messageId,
      Boolean solved,
      String reasonCode,
      String payloadJson,
      String freeText,
      Boolean recoveryActionTaken,
      Instant createdAt,
      Instant updatedAt) {

    public boolean safeSolved() {
      return solved != null && solved;
    }

    public boolean safeRecovery() {
      return recoveryActionTaken != null && recoveryActionTaken;
    }
  }

  /** Mirrors {@code ds_session_share}. {@code revokedAt} is null for an active share. */
  public record ShareRow(
      String id,
      String tenantId,
      String sessionId,
      String ownerUserId,
      String tokenHash,
      Instant expiresAt,
      Instant revokedAt,
      Instant createdAt) {}
}
