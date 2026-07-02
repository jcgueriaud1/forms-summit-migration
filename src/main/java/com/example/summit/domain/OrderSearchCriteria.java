package com.example.summit.domain;

import java.time.LocalDate;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Pure value object for the ORDERS search: it turns the clerk's typed term <em>and</em> the
 * advanced-panel filters into the match components the repository renders as SQL. This is the
 * faithful web rendering of Forms' Enter-Query on the single-record {@code S_ORD} block — the
 * quick "find by order id, customer, or sales rep" plus the QBE-style multi-field narrowing
 * (issue #43).
 *
 * <p>Domain-pure (ADR-0001): no Spring, no Vaadin, no SQL. It decides <em>what</em> to
 * match, never <em>how</em> to query, which is what makes it unit-testable in
 * isolation. The matching contract:
 * <ul>
 *   <li>a blank quick term constrains nothing on its own; an all-digit term may additionally
 *       match {@code S_ORD.ID} ({@link #orderId()}), and any term matches customer or sales-rep
 *       name case-insensitively via the quick contains-pattern {@link #namePattern()} — the three
 *       combined with <em>OR</em> (the quick group);</li>
 *   <li>each advanced filter that is set — {@link #customerPattern() customer} and
 *       {@link #salesRepPattern() sales-rep} contains-patterns, {@link #paymentType() payment type},
 *       the {@link #orderedFrom()}/{@link #orderedTo()} inclusive ordered-date range, and the
 *       {@link #status() order status} — is an <em>additional</em> constraint;</li>
 *   <li>the quick group and every set advanced filter combine with <strong>AND</strong>, so a
 *       clerk narrows precisely;</li>
 *   <li>criteria with nothing set at all place no constraint — every order matches
 *       ({@link #isEmpty()}).</li>
 * </ul>
 */
public record OrderSearchCriteria(
        String quickTerm,
        String customer,
        String salesRep,
        String paymentType,
        LocalDate orderedFrom,
        LocalDate orderedTo,
        OrderStatus status) {

    /**
     * The {@code S_ORD.ORDER_FILLED} state the advanced panel filters on — the web form of Forms'
     * Y/N flag. {@link #FILLED} matches {@code 'Y'}, {@link #UNFILLED} matches {@code 'N'}.
     */
    public enum OrderStatus {
        FILLED("Y"),
        UNFILLED("N");

        private final String flag;

        OrderStatus(String flag) {
            this.flag = flag;
        }

        /** The {@code S_ORD.ORDER_FILLED} Y/N code this status matches. */
        public String flag() {
            return flag;
        }
    }

    public OrderSearchCriteria {
        quickTerm = blankToEmpty(quickTerm);
        customer = blankToEmpty(customer);
        salesRep = blankToEmpty(salesRep);
        paymentType = blankToEmpty(paymentType);
        // Dates and status pass through: a null bound / null status is simply unset.
    }

    /** Quick-term-only criteria — the autocomplete's per-keystroke search with no advanced narrowing. */
    public OrderSearchCriteria(String quickTerm) {
        this(quickTerm, null, null, null, null, null, null);
    }

    /**
     * True when nothing at all is set — no quick term and no advanced filter — so the result set is
     * every order. Distinct from {@link #hasQuickTerm()}: advanced-only criteria are not empty.
     */
    public boolean isEmpty() {
        return !hasQuickTerm() && customer.isEmpty() && salesRep.isEmpty()
                && paymentType.isEmpty() && orderedFrom == null && orderedTo == null && status == null;
    }

    // ---- Quick term (the OR group over id / customer / sales rep) ------------------------------

    /** True when the clerk typed a quick term — the OR group over order id / customer / sales rep. */
    public boolean hasQuickTerm() {
        return !quickTerm.isEmpty();
    }

    /** True when the quick term is all digits and can therefore be matched against {@code S_ORD.ID}. */
    public boolean matchesOrderId() {
        return hasQuickTerm() && quickTerm.chars().allMatch(Character::isDigit);
    }

    /**
     * The order id to match when the quick term is a parseable id. Empty when the term is
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
     * A case-insensitive contains-pattern for the quick-term name match; pair with
     * {@code UPPER(column) LIKE :pattern} in the query.
     */
    public String namePattern() {
        return containsPattern(quickTerm);
    }

    // ---- Advanced filters (each an additional AND constraint) ----------------------------------

    /** A case-insensitive contains-pattern for the advanced customer-name filter, when it is set. */
    public Optional<String> customerPattern() {
        return customer.isEmpty() ? Optional.empty() : Optional.of(containsPattern(customer));
    }

    /** A case-insensitive contains-pattern for the advanced sales-rep-name filter, when it is set. */
    public Optional<String> salesRepPattern() {
        return salesRep.isEmpty() ? Optional.empty() : Optional.of(containsPattern(salesRep));
    }

    /** True when the advanced payment-type filter ({@code CASH}/{@code CREDIT}) is set. */
    public boolean hasPaymentType() {
        return !paymentType.isEmpty();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.strip();
    }

    private static String containsPattern(String value) {
        return "%" + value.toUpperCase() + "%";
    }
}
