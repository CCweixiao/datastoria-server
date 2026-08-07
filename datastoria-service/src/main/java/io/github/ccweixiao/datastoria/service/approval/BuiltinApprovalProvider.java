package io.github.ccweixiao.datastoria.service.approval;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalRequest;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalStatus;
import io.github.ccweixiao.datastoria.common.error.ApiErrorCode;
import io.github.ccweixiao.datastoria.common.error.ConflictException;
import io.github.ccweixiao.datastoria.common.identity.Identity;

/**
 * Built-in approval provider. Enforces separation of duties: by default an approver cannot approve
 * their own submission (V3 §5.3 routing rule). {@code datastoria.approval.allow-self-approve} opts
 * back into self-approval for single-admin/dev deployments.
 */
@Component
public class BuiltinApprovalProvider implements ApprovalProvider {

  private final boolean allowSelfApprove;

  public BuiltinApprovalProvider(
      @Value("${datastoria.approval.allow-self-approve:false}") boolean allowSelfApprove) {
    this.allowSelfApprove = allowSelfApprove;
  }

  @Override
  public String key() {
    return BUILTIN_KEY;
  }

  @Override
  public ApprovalStatus decide(ApprovalRequest request, boolean approve, Identity actor) {
    if (approve
        && !allowSelfApprove
        && request.applicantUserId() != null
        && request.applicantUserId().equals(actor.userId())) {
      throw new ConflictException(ApiErrorCode.APPROVAL_SELF_APPROVAL_FORBIDDEN);
    }
    return approve ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED;
  }
}
