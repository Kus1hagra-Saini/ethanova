package com.ethanova.backend.dispatch;

import java.util.Set;

/**
 * Lifecycle states for a {@link DispatchOrder}.
 *
 * <p>The transition rules ({@link #canTransitionTo(DispatchStatus)}) are
 * defined here — on the enum itself — rather than scattered as {@code if}
 * chains inside the service. This keeps the domain rule with the domain
 * type and gives a single point of truth for viva and audit purposes.
 *
 * <p>Rules in prose:
 * <ul>
 *   <li>Progression is linear: DRAFT → CONFIRMED → DISPATCHED → IN_TRANSIT → DELIVERED</li>
 *   <li>Cancellation is allowed from any non-terminal state</li>
 *   <li>{@link #DELIVERED} and {@link #CANCELLED} are terminal — no further transitions</li>
 * </ul>
 */
public enum DispatchStatus {

    DRAFT,
    CONFIRMED,
    DISPATCHED,
    IN_TRANSIT,
    DELIVERED,
    CANCELLED;

    /**
     * @return true if this order may transition from {@code this} to {@code target}.
     */
    public boolean canTransitionTo(DispatchStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case DRAFT      -> target == CONFIRMED  || target == CANCELLED;
            case CONFIRMED  -> target == DISPATCHED || target == CANCELLED;
            case DISPATCHED -> target == IN_TRANSIT || target == CANCELLED;
            case IN_TRANSIT -> target == DELIVERED  || target == CANCELLED;
            case DELIVERED, CANCELLED -> false; // terminal
        };
    }

    /**
     * @return true if no further transitions are permitted from this state.
     */
    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED;
    }

    /**
     * Convenience: statuses which permit cancellation (all non-terminal ones).
     * Not currently used by the service but useful for UI or audit tooling.
     */
    public static Set<DispatchStatus> cancellableStates() {
        return Set.of(DRAFT, CONFIRMED, DISPATCHED, IN_TRANSIT);
    }
}