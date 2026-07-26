package io.datastoria.server.persistence.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * Dedicated mapper for the P3 importer's idempotent lookup-then-upsert across the four P3 tables.
 * Timestamps are bound as raw ISO-8601 strings (the row's {@code Instant.toString()}), preserving
 * the original JDBC behaviour exactly; the per-table transaction/rollback wrapping is kept in
 * {@code P3Importer} via {@code TransactionTemplate}.
 */
public interface P3ImportMapper {

  // ---- ds_chat_session ----
  Integer sessionExists(@Param("tenantId") String tenantId, @Param("id") String id);

  int insertSession(
      @Param("id") String id,
      @Param("tenantId") String tenantId,
      @Param("userId") String userId,
      @Param("connectionId") String connectionId,
      @Param("title") String title,
      @Param("revision") long revision,
      @Param("createdAt") String createdAt,
      @Param("updatedAt") String updatedAt);

  int updateSession(
      @Param("userId") String userId,
      @Param("connectionId") String connectionId,
      @Param("title") String title,
      @Param("revision") long revision,
      @Param("updatedAt") String updatedAt,
      @Param("tenantId") String tenantId,
      @Param("id") String id);

  // ---- ds_chat_message ----
  Integer messageExists(
      @Param("tenantId") String tenantId,
      @Param("sessionId") String sessionId,
      @Param("id") String id);

  int insertMessage(
      @Param("id") String id,
      @Param("tenantId") String tenantId,
      @Param("sessionId") String sessionId,
      @Param("userId") String userId,
      @Param("role") String role,
      @Param("partsJson") String partsJson,
      @Param("metadataJson") String metadataJson,
      @Param("sequence") long sequence,
      @Param("createdAt") String createdAt,
      @Param("updatedAt") String updatedAt);

  int updateMessage(
      @Param("userId") String userId,
      @Param("role") String role,
      @Param("partsJson") String partsJson,
      @Param("metadataJson") String metadataJson,
      @Param("sequence") long sequence,
      @Param("updatedAt") String updatedAt,
      @Param("tenantId") String tenantId,
      @Param("sessionId") String sessionId,
      @Param("id") String id);

  // ---- ds_feedback_event ----
  Integer feedbackExists(
      @Param("tenantId") String tenantId,
      @Param("userId") String userId,
      @Param("source") String source,
      @Param("sessionId") String sessionId,
      @Param("messageId") String messageId);

  int insertFeedback(
      @Param("id") String id,
      @Param("tenantId") String tenantId,
      @Param("userId") String userId,
      @Param("source") String source,
      @Param("sessionId") String sessionId,
      @Param("messageId") String messageId,
      @Param("solved") boolean solved,
      @Param("reasonCode") String reasonCode,
      @Param("payloadJson") String payloadJson,
      @Param("freeText") String freeText,
      @Param("recoveryActionTaken") boolean recoveryActionTaken,
      @Param("createdAt") String createdAt,
      @Param("updatedAt") String updatedAt);

  int updateFeedback(
      @Param("solved") boolean solved,
      @Param("reasonCode") String reasonCode,
      @Param("payloadJson") String payloadJson,
      @Param("freeText") String freeText,
      @Param("recoveryActionTaken") boolean recoveryActionTaken,
      @Param("updatedAt") String updatedAt,
      @Param("tenantId") String tenantId,
      @Param("userId") String userId,
      @Param("source") String source,
      @Param("sessionId") String sessionId,
      @Param("messageId") String messageId);

  // ---- ds_session_share ----
  Integer shareExists(@Param("tenantId") String tenantId, @Param("tokenHash") String tokenHash);

  int insertShare(
      @Param("id") String id,
      @Param("tenantId") String tenantId,
      @Param("sessionId") String sessionId,
      @Param("ownerUserId") String ownerUserId,
      @Param("tokenHash") String tokenHash,
      @Param("expiresAt") String expiresAt,
      @Param("revokedAt") String revokedAt,
      @Param("createdAt") String createdAt);

  int updateShare(
      @Param("expiresAt") String expiresAt,
      @Param("revokedAt") String revokedAt,
      @Param("tenantId") String tenantId,
      @Param("tokenHash") String tokenHash);
}
