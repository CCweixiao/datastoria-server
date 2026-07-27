package io.datastoria.server.agent.runtime;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.reactivestreams.Subscription;

import io.datastoria.server.agent.domain.RunContext;

/**
 * Tracks active runs so a server-initiated cancel (or the {@code POST /runs/{id}:cancel} endpoint)
 * can stop a run by id. Cancelling a run performs both:
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
 * invariant the design requires independent of controller-layer authorization.
 */
public final class CancellationRegistry {

  private final ConcurrentHashMap<String, Registration> runs = new ConcurrentHashMap<>();

  private record Registration(
      RunnableAgent agent,
      AtomicReference<Subscription> subscription,
      AtomicBoolean cancelled,
      String tenantId,
      String userId) {}

  /**
   * Atomically registers a run as active.
   *
   * @return false when the same run id is already active; the existing registration is untouched.
   */
  public boolean register(RunContext context, RunnableAgent agent) {
    Registration registration =
        new Registration(
            agent,
            new AtomicReference<>(),
            new AtomicBoolean(),
            context.tenantId(),
            context.userId());
    return runs.putIfAbsent(context.runId(), registration) == null;
  }

  /**
   * Binds the live subscription for a run, enabling reliable dispose-based cancellation. Called by
   * the subscriber (P4.6 controller on subscribe; tests directly).
   */
  public void bindSubscription(String runId, RunnableAgent agent, Subscription subscription) {
    Registration registration = runs.get(runId);
    if (registration != null && registration.agent() == agent) {
      registration.subscription().set(subscription);
      // Covers cancel arriving after register but before Reactor invokes doOnSubscribe.
      if (registration.cancelled().get()) {
        subscription.cancel();
      }
    } else {
      // A stale/duplicate run must never be allowed to continue without registry ownership.
      subscription.cancel();
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
    registration.cancelled().set(true);
    Subscription subscription = registration.subscription().get();
    if (subscription != null) {
      subscription.cancel();
    }
    registration.agent().interrupt();
    return true;
  }

  /** Removes a run from the active set (called on terminal complete/error/cancel). */
  public void unregister(String runId, RunnableAgent agent) {
    runs.computeIfPresent(runId, (ignored, current) -> current.agent() == agent ? null : current);
  }
}
