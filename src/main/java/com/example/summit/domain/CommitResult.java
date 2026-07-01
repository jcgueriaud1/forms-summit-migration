package com.example.summit.domain;

/**
 * The typed outcome of the {@code COMMIT_FORM} whole-graph save (issue #19) — the web counterpart
 * of what Forms surfaced through its commit machinery, but as a value rather than a modal alert.
 * The {@code Service.save} commit is the authoritative gate (ADR-0002): every save resolves to
 * exactly one of these, so a stale or invalid save <em>fails as a result the caller must handle</em>
 * rather than silently overwriting or half-persisting.
 *
 * <ul>
 *   <li>{@link Committed} — the header and all line items were persisted as one unit; carries the
 *       persisted order id (a new order's id comes from {@code S_ORD_ID.nextval}).</li>
 *   <li>{@link Conflict} — the order changed since it was loaded (its {@code ORA_ROWSCN} moved), so
 *       the optimistic lock refused the stale save. Nothing was overwritten
 *       ({@code concurrency-pessimistic-to-scn}).</li>
 *   <li>{@link Rejected} — the commit gate refused still-invalid input; nothing was persisted.</li>
 * </ul>
 */
public sealed interface CommitResult {

    /** The whole-graph save succeeded; {@code orderId} is the persisted order's id. */
    record Committed(long orderId) implements CommitResult {
    }

    /**
     * A concurrent change since load was detected via {@code ORA_ROWSCN}; the stale save was
     * refused and nothing was overwritten.
     */
    record Conflict() implements CommitResult {
    }

    /** The commit gate refused still-invalid input; {@code message} explains why. Nothing persisted. */
    record Rejected(String message) implements CommitResult {
    }
}
