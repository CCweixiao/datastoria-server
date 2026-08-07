package io.github.ccweixiao.datastoria.service.approval;

import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalRequest;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalStatus;
import io.github.ccweixiao.datastoria.common.identity.Identity;

/**
 * Approval decision provider. The built-in implementation enforces routing policy (e.g.
 * self-approval is forbidden) and records the reviewer's synchronous decision. External providers
 * (DingTalk / Feishu / Jira) would implement async submission + callback, mapping their result to
 * the same {@link ApprovalStatus} outcomes.
 */
public interface ApprovalProvider {

  String BUILTIN_KEY = "builtin";

  String key();

  /**
   * Validates an approval/rejection action against routing policy and returns the target status.
   * Throws on policy violations (e.g. an approver reviewing their own submission).
   */
  ApprovalStatus decide(ApprovalRequest request, boolean approve, Identity actor);
}
