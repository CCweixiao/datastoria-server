package io.github.ccweixiao.datastoria.service.approval;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.github.ccweixiao.datastoria.common.config.JdbcSchedulerConfig;

import reactor.core.scheduler.Scheduler;

/**
 * Polls claimable QUEUED work orders (AUTO_AFTER_APPROVAL) on a schedule and drains them via {@link
 * ApprovalCommandService#drainOnce()}. Overlap between firings is guarded by {@code busy}; a drain
 * already in progress causes the next tick to skip. Concurrency across instances is safe because
 * {@code claimQueued} is a CAS on (revision, status, lease).
 */
@Component
public class ApprovalExecutionWorker {

  private static final Logger log = LoggerFactory.getLogger(ApprovalExecutionWorker.class);

  private final ApprovalCommandService service;
  private final Scheduler jdbcScheduler;
  private final AtomicBoolean busy = new AtomicBoolean(false);

  public ApprovalExecutionWorker(
      ApprovalCommandService service,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler) {
    this.service = service;
    this.jdbcScheduler = jdbcScheduler;
  }

  @Scheduled(fixedDelayString = "${datastoria.approval.execution-poll-ms:10000}")
  public void drain() {
    if (!busy.compareAndSet(false, true)) {
      return;
    }
    service
        .drainOnce()
        .subscribeOn(jdbcScheduler)
        .doFinally(signal -> busy.set(false))
        .subscribe(unused -> {}, error -> log.warn("Approval execution drain failed", error));
  }
}
