package com.example.summit.domain;

import java.util.OptionalLong;

/**
 * Pure value object for the ORDERS quick search: it turns the clerk's typed term into
 * the match components the repository renders as SQL. This is the faithful web
 * rendering of Forms' Enter-Query on the single-record {@code S_ORD} block — "find by
 * order id, customer, or sales rep".
 *
 * <p>Domain-pure (ADR-0001): no Spring, no Vaadin, no SQL. It decides <em>what</em> to
 * match, never <em>how</em> to query, which is what makes it unit-testable in
 * isolation. The matching contract:
 * <ul>
 *   <li>a blank term constrains nothing — every order matches ({@link #isEmpty()});</li>
 *   <li>an all-digit term may additionally match {@code S_ORD.ID} ({@link #orderId()});</li>
 *   <li>any term matches customer or sales-rep name case-insensitively via the
 *       contains-pattern {@link #namePattern()}.</li>
 * </ul>
 */
public record OrderSearchCriteria(String quickTerm) {

    public OrderSearchCriteria {
        quickTerm = quickTerm == null ? "" : quickTerm.strip();
    }

    /** A blank term places no constraint: the result set is every order. */
    public boolean isEmpty() {
        return quickTerm.isEmpty();
    }

    /** True when the term is all digits and can therefore be matched against {@code S_ORD.ID}. */
    public boolean matchesOrderId() {
        return !quickTerm.isEmpty() && quickTerm.chars().allMatch(Character::isDigit);
    }

    /**
     * The order id to match when the term is a parseable id. Empty when the term is
     * non-numeric, or all-digits but too large for a {@code long} — the caller then
     * relies on the {@link #namePattern() name match} alone rather than failing.
     */
    public OptionalLong orderId() {
        if (!matchesOrderId()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseLong(quickTerm));
        } catch (NumberFormatException tooLargeForAnId) {
            return OptionalLong.empty();
        }
    }

    /**
     * A case-insensitive contains-pattern for the name match; pair with
     * {@code UPPER(column) LIKE :pattern} in the query.
     */
    public String namePattern() {
        return "%" + quickTerm.toUpperCase() + "%";
    }
}
