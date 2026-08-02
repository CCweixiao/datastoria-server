package io.github.ccweixiao.datastoria.common.dto.approval;

import com.fasterxml.jackson.databind.JsonNode;

public record DdlApprovalPrepareRequest(
    String connectionId,
    String workOrderTypeKey,
    String title,
    String summary,
    JsonNode intent,
    String sourceSessionId,
    String sourceRunId,
    String draftId,
    Long expectedRevision) {}
