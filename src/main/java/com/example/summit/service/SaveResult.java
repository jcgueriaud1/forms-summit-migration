package com.example.summit.service;

import com.example.summit.domain.Order;

/**
 * The typed outcome of a whole-graph {@code COMMIT_FORM} save (issue #48) — the commit either lands
 * the order, is refused by the concurrency gate, or is refused by the validation gate. Modelling it
 * as a sealed result (rather than a bare boolean or a thrown exception) lets the view react to each
 * case distinctly — reflect the saved order, warn about a concurrent change, or point at the invalid
 * input — and keeps the "no silent overwrite" guarantee ({@code concurrency-pessimistic-to-scn})
 * legible in the type.
 */
public sealed interface SaveResult
        permits SaveResult.Committed, SaveResult.Conflict, SaveResult.Invalid {

    /**
     * The commit succeeded: {@code order} is the freshly reloaded graph — new id (for an inserted
     * order), the persisted lines, and the advanced {@code ORA_ROWSCN} token — so the view can keep
     * editing and re-commit against the current row version without a manual reload.
     */
    record Committed(Order order) implements SaveResult {}

    /**
     * A concurrent commit changed the {@code S_ORD} row since it was loaded (its {@code ORA_ROWSCN}
     * no longer matches): the guarded header update matched no row, so <em>nothing was written</em>
     * and the concurrent change stands — the stale save fails rather than silently overwriting it.
     */
    record Conflict() implements SaveResult {}

    /**
     * The commit gate refused still-invalid input (ADR-0002: the Service commit is the authoritative
     * gate, so nothing invalid persists). {@code message} explains which invariant was violated.
     */
    record Invalid(String message) implements SaveResult {}

    static SaveResult committed(Order order) {
        return new Committed(order);
    }

    static SaveResult conflict() {
        return new Conflict();
    }

    static SaveResult invalid(String message) {
        return new Invalid(message);
    }
}
