package com.example.summit.domain;

import java.util.OptionalLong;

/**
 * Pure, immutable value object for the quick search — it translates a clerk's typed
 * term into the predicate components {@code OrderRepository.findByCriteria} turns into
 * SQL. It is the faithful web rendering of Forms' Enter-Query for the
 * single-record {@code S_ORD} block: "by order id / customer / sales rep".
 *
 * <p>Domain-pure (ADR-0001): no Spring, no Vaadin, no SQL — it decides <em>what</em>
 * to match, not <em>how</em> to query. The rules:
 * <ul>
 *   <li>a blank term matches every order (empty criteria);</li>
 *   <li>an all-digit term can match the {@code S_ORD.ID} as well as the names;</li>
 *   <li>any term matches customer name or sales-rep name, case-insensitively
 *       ({@link #namePattern()} is a contains-pattern).</li>
 * </ul>
 */
public record OrderSearchCriteria(String quickTerm) {

    public OrderSearchCriteria {
        quickTerm = quickTerm == null ? "" : quickTerm.strip();
    }

    /** A blank quick term places no constraint — the result set is every order. */
    public boolean isEmpty() {
        return quickTerm.isEmpty();
    }

    /** True when the term is all digits, so it can be matched against {@code S_ORD.ID}. */
    public boolean matchesOrderId() {
        return !quickTerm.isEmpty() && quickTerm.chars().allMatch(Character::isDigit);
    }

    /**
     * The order id to match, when the term is a parseable order id. Empty when the
     * term is non-numeric or too large to be an id — callers then fall back to the
     * {@link #namePattern() name match} alone.
     */
    public OptionalLong orderId() {
        if (!matchesOrderId()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseLong(quickTerm));
        } catch (NumberFormatException overflow) {
            return OptionalLong.empty();
        }
    }

    /**
     * Case-insensitive contains-pattern for matching customer / sales-rep names.
     * Pair with {@code UPPER(column) LIKE :pattern} in the query.
     */
    public String namePattern() {
        return "%" + quickTerm.toUpperCase() + "%";
    }
}
