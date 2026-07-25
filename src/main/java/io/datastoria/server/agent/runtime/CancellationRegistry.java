package io.datastoria.server.agent.runtime;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import io.datastoria.server.agent.domain.RunContext;

import reactor.core.Disposable;

/**
 * Tracks active runs so a server-initiated cancel (or the {@code POST /runs/{id}:cancel} endpoint
 * in P4.6) can stop a run by id. Per ADR-0004 §3.3, cancelling a run performs BOTH:
 *
 * <ol>
 *   <li>dispose the {@code streamEvents} subscription — the reliable primitive that cancels the
 *       provider model flux and stops token emission; and
 *   <li>{@code agent.interrupt()} — cooperative, step-boundary cleanup for multi-step runs.
 * </ol>
 *
 * <p><b>Tenant isolation:</b> {@link #cancel} succeeds only when the requester matches the run
 * owner recorded at registration. A tenant/user can never cancel another tenant's run; a mismatched
 * request is a no-op that returns {@code false} and touches nothing. This is the defense-in-depth
 * invariant the design requires (docs/design/api-contracts.md §9, §10) independent of any
 * controller-layer authorization added in P4.6.
 */
public final class CancellationRegistry {

  private final ConcurrentHashMap<String, Registration> runs = new ConcurrentHashMap<>();

  private record Registration(
      RunnableAgent agent,
      AtomicReference<Disposable> subscription,
      String tenantId,
      String userId) {}

  /** Registers a run as active; the subscription is bound later via {@link #bindSubscription}. */
  public void register(RunContext context, RunnableAgent agent) {
    runs.put(
        context.runId(),
        new Registration(agent, new AtomicReference<>(), context.tenantId(), context.userId()));
  }

  /**
   * Binds the live subscription for a run, enabling reliable dispose-based cancellation. Called by
   * the subscriber (P4.6 controller on subscribe; tests directly).
   */
  public void bindSubscription(String runId, Disposable subscription) {
    Registration registration = runs.get(runId);
    if (registration != null) {
      registration.subscription().set(subscription);
    }
  }

  public boolean isActive(String runId) {
    return runs.containsKey(runId);
  }

  /**
   * Cancels the run iff {@code (requesterTenantId, requesterUserId)} owns it. Disposes the bound
   * subscription (stops provider tokens) and cooperatively interrupts the agent.
   *
   * @return {@code true} if the run was owned by the requester and cancellation was issued.
   */
  public boolean cancel(String runId, String requesterTenantId, String requesterUserId) {
    Registration registration = runs.get(runId);
    if (registration == null) {
      return false;
    }
    if (!registration.tenantId().equals(requesterTenantId)
        || !registration.userId().equals(requesterUserId)) {
      return false;
    }
    Disposable subscription = registration.subscription().get();
    if (subscription != null && !subscription.isDisposed()) {
      subscription.dispose();
    }
    registration.agent().interrupt();
    return true;
  }

  /** Removes a run from the active set (called on terminal complete/error/cancel). */
  public void unregister(String runId) {
    runs.remove(runId);
  }
}
