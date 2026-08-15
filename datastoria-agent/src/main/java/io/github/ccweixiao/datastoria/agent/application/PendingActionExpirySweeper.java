package io.github.ccweixiao.datastoria.agent.application;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.github.ccweixiao.datastoria.dao.repository.AgentPendingActionRepository;

/**
 * Periodically flips due pending HITL actions (questions left unanswered past their {@code
 * expires_at}) to {@code expired}. Correctness does not depend on the sweep — resolving an expired
 * action is already rejected at answer time — but it keeps abandoned runs from lingering as
 * answerable-looking rows and keeps the pending queue clean for operators.
 *
 * <p>The sweep runs on the scheduler thread pool (never a Netty event loop), so the blocking
 * repository call is safe here.
 */
@Component
public class PendingActionExpirySweeper {

  private static final Logger log = LoggerFactory.getLogger(PendingActionExpirySweeper.class);

  private final AgentPendingActionRepository pendingActions;

  public PendingActionExpirySweeper(AgentPendingActionRepository pendingActions) {
    this.pendingActions = pendingActions;
  }

  @Scheduled(
      fixedDelayString = "${datastoria.agent.pending-action-sweep-interval-ms:60000}",
      initialDelay = 60000)
  public void sweepExpiredPendingActions() {
    int expired = pendingActions.expireDue(Instant.now());
    if (expired > 0) {
      log.info("Expired {} due pending agent action(s)", expired);
    }
  }
}
