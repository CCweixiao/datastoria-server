package io.github.ccweixiao.datastoria.common.dto.approval;

public record ApprovalTransitionRequest(long revision, String contentDigest, String comment) {}
