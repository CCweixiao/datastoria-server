package io.github.ccweixiao.datastoria.common.dto.approval;

import java.util.List;

import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalRequest;

public record ApprovalPageResponse(
    List<ApprovalRequest> items, long total, int page, int pageSize) {}
