package com.example.summit.domain;

import java.time.LocalDate;
import java.util.OptionalLong;

/**
 * Pure, immutable value object for the order lookup — the faithful web rendering of Forms'
 * Enter-Query for the single-record {@code S_ORD} block. It carries the clerk's <em>quick
 * term</em> ("by order id / customer / sales rep") and the <em>advanced</em>, QBE-style
 * filters (customer, sales rep, payment type, ordered date-range, order-filled status), and
 * translates them into the predicate components {@code OrderRepository.findByCriteria} turns
 * into SQL.
 *
 * <p>Domain-pure (ADR-0001): no Spring, no Vaadin, no SQL — it decides <em>what</em> to match,
 * not <em>how</em> to query. The combination rule is the QBE one:
 * <ul>
 *   <li>the quick term matches the {@code S_ORD.ID} <em>or</em> the customer / sales-rep name
 *       (an OR-group — {@link #namePattern()} is a case-insensitive contains-pattern);</li>
 *   <li>each advanced filter that is set narrows the result further — they combine with the
 *       quick term and with each other by <strong>AND</strong>;</li>
 *   <li>a criteria with nothing set ({@link #isEmpty()}) places no constraint — every order
 *       matches.</li>
 * </ul>
 *
 * <p>Blank text is normalised away (a blank advanced field is "not set", same as null), so an
 * untouched advanced panel contributes no predicate. The ordered date-range bounds are
 * <strong>inclusive</strong> on both ends — see {@link #matchesOrderedDate(LocalDate)}, the
 * canonical contract the SQL mirrors.
 */
public record OrderSearchCriteria(
        String quickTerm,
        String customer,
        String salesRep,
        String paymentType,
        LocalDate orderedFrom,
        LocalDate orderedTo,
        Boolean orderFilled) {

    public OrderSearchCriteria {
        quickTerm = quickTerm == null ? "" : quickTerm.strip();
        customer = blankToNull(customer);
        salesRep = blankToNull(salesRep);
        paymentType = blankToNull(paymentType);
    }

    /** Convenience constructor for the quick term alone — no advanced filters set. */
    public OrderSearchCriteria(String quickTerm) {
        this(quickTerm, null, null, null, null, null, null);
    }

    /** No quick term and no advanced filter — the result set is then every order. */
    public boolean isEmpty() {
        return !hasQuickTerm()
                && customer == null
                && salesRep == null
                && paymentType == null
                && orderedFrom == null
                && orderedTo == null
                && orderFilled == null;
    }

    // ---- Quick term (id / customer / sales-rep name OR-group) ---------------------------

    /** True when the clerk typed a quick term (the id-or-name OR-group is then active). */
    public boolean hasQuickTerm() {
        return !quickTerm.isEmpty();
    }

    /** True when the term is all digits, so it can be matched against {@code S_ORD.ID}. */
    public boolean matchesOrderId() {
        return hasQuickTerm() && quickTerm.chars().allMatch(Character::isDigit);
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
     * Case-insensitive contains-pattern for the quick term against customer / sales-rep names.
     * Pair with {@code UPPER(column) LIKE :pattern} in the query.
     */
    public String namePattern() {
        return likePattern(quickTerm);
    }

    // ---- Advanced filters (each AND-narrows the result) ---------------------------------

    public boolean hasCustomer() {
        return customer != null;
    }

    /** Case-insensitive contains-pattern for the advanced customer-name filter. */
    public String customerPattern() {
        return likePattern(customer);
    }

    public boolean hasSalesRep() {
        return salesRep != null;
    }

    /** Case-insensitive contains-pattern for the advanced sales-rep-name filter. */
    public String salesRepPattern() {
        return likePattern(salesRep);
    }

    public boolean hasPaymentType() {
        return paymentType != null;
    }

    public boolean hasOrderedFrom() {
        return orderedFrom != null;
    }

    public boolean hasOrderedTo() {
        return orderedTo != null;
    }

    /** True when the order-filled status is constrained ({@code true}=filled, {@code false}=not). */
    public boolean hasOrderFilled() {
        return orderFilled != null;
    }

    /**
     * Whether {@code date} falls inside the ordered date-range — the canonical, inclusive
     * contract for the {@code orderedFrom}/{@code orderedTo} bounds. An unset bound is open:
     * a null {@code orderedFrom} means "no lower bound", a null {@code orderedTo} means "no
     * upper bound". The {@code findByCriteria} SQL mirrors this with {@code >=} / {@code <=}.
     */
    public boolean matchesOrderedDate(LocalDate date) {
        if (date == null) {
            return !hasOrderedFrom() && !hasOrderedTo();
        }
        if (hasOrderedFrom() && date.isBefore(orderedFrom)) {
            return false;
        }
        return !hasOrderedTo() || !date.isAfter(orderedTo);
    }

    private static String likePattern(String value) {
        return "%" + value.toUpperCase() + "%";
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
