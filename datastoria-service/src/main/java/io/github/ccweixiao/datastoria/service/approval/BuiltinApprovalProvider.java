package io.github.ccweixiao.datastoria.service.approval;

import org.springframework.stereotype.Component;

import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalRequest;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalStatus;
import io.github.ccweixiao.datastoria.common.identity.Identity;

/**
 * Built-in approval provider. Records the reviewer's synchronous decision with no further policy
 * checks; self-approval is permitted. External providers (DingTalk / Feishu / Jira) would implement
 * async submission + callback, mapping their result to the same {@link ApprovalStatus} outcomes.
 */
@Component
public class BuiltinApprovalProvider implements ApprovalProvider {

  @Override
  public String key() {
    return BUILTIN_KEY;
  }

  @Override
  public ApprovalStatus decide(ApprovalRequest request, boolean approve, Identity actor) {
    return approve ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED;
  }
}
