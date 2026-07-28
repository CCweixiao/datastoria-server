package io.github.ccweixiao.datastoria.dao.persistence.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Param;

/**
 * Dedicated mapper for the P3 importer's idempotent lookup-then-upsert across the four P3 tables.
 * Timestamps are bound as {@link Instant} values through the shared cross-dialect type handler; the
 * per-table transaction/rollback wrapping is kept in {@code P3Importer} via {@code
 * TransactionTemplate}.
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
      @Param("createdAt") Instant createdAt,
      @Param("updatedAt") Instant updatedAt);

  int updateSession(
      @Param("userId") String userId,
      @Param("connectionId") String connectionId,
      @Param("title") String title,
      @Param("revision") long revision,
      @Param("updatedAt") Instant updatedAt,
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
      @Param("createdAt") Instant createdAt,
      @Param("updatedAt") Instant updatedAt);

  int updateMessage(
      @Param("userId") String userId,
      @Param("role") String role,
      @Param("partsJson") String partsJson,
      @Param("metadataJson") String metadataJson,
      @Param("sequence") long sequence,
      @Param("updatedAt") Instant updatedAt,
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
      @Param("createdAt") Instant createdAt,
      @Param("updatedAt") Instant updatedAt);

  int updateFeedback(
      @Param("solved") boolean solved,
      @Param("reasonCode") String reasonCode,
      @Param("payloadJson") String payloadJson,
      @Param("freeText") String freeText,
      @Param("recoveryActionTaken") boolean recoveryActionTaken,
      @Param("updatedAt") Instant updatedAt,
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
      @Param("expiresAt") Instant expiresAt,
      @Param("revokedAt") Instant revokedAt,
      @Param("createdAt") Instant createdAt);

  int updateShare(
      @Param("expiresAt") Instant expiresAt,
      @Param("revokedAt") Instant revokedAt,
      @Param("tenantId") String tenantId,
      @Param("tokenHash") String tokenHash);
}
