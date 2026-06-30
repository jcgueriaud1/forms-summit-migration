package com.example.summit.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code OrderSearchCriteria} is pure (no Spring, no DB) — the quick-term → predicate
 * translation is unit-testable in isolation, same property as {@code ServerInfoFormulaTest}.
 */
class OrderSearchCriteriaTest {

    @Test
    void blankTermMatchesEveryOrder() {
        assertTrue(new OrderSearchCriteria("").isEmpty());
        assertTrue(new OrderSearchCriteria("   ").isEmpty());
        assertTrue(new OrderSearchCriteria(null).isEmpty());
    }

    @Test
    void termIsTrimmed() {
        assertEquals("Magee", new OrderSearchCriteria("  Magee  ").quickTerm());
    }

    @Test
    void numericTermCanMatchOrderId() {
        OrderSearchCriteria criteria = new OrderSearchCriteria("50002");
        assertTrue(criteria.matchesOrderId());
        assertTrue(criteria.orderId().isPresent());
        assertEquals(50002L, criteria.orderId().getAsLong());
    }

    @Test
    void nonNumericTermDoesNotMatchOrderId() {
        OrderSearchCriteria criteria = new OrderSearchCriteria("Womansport");
        assertFalse(criteria.matchesOrderId());
        assertTrue(criteria.orderId().isEmpty());
    }

    @Test
    void oversizedNumericTermFallsBackToNameMatchOnly() {
        // All digits but too large for a long — cannot be an order id; name match still applies.
        OrderSearchCriteria criteria = new OrderSearchCriteria("999999999999999999999999");
        assertTrue(criteria.matchesOrderId());
        assertTrue(criteria.orderId().isEmpty());
    }

    @Test
    void namePatternIsAnUppercasedContainsPattern() {
        assertEquals("%MAGEE%", new OrderSearchCriteria("magee").namePattern());
        assertEquals("%UNISPORTS%", new OrderSearchCriteria("Unisports").namePattern());
    }

    // ---- Advanced search (issue #14): quick alone / advanced alone / combined (AND) ----------

    @Test
    void emptyCriteriaMatchesEveryOrder() {
        // Nothing set, by any constructor — no quick term and no advanced filter.
        assertTrue(new OrderSearchCriteria(null).isEmpty());
        assertTrue(new OrderSearchCriteria("  ", "  ", null, "  ", null, null, null).isEmpty());
    }

    @Test
    void quickTermAloneSetsNoAdvancedPredicate() {
        OrderSearchCriteria criteria = new OrderSearchCriteria("Magee");

        assertFalse(criteria.isEmpty());
        assertTrue(criteria.hasQuickTerm());
        assertEquals("%MAGEE%", criteria.namePattern());
        // No advanced filter is active.
        assertFalse(criteria.hasCustomer());
        assertFalse(criteria.hasSalesRep());
        assertFalse(criteria.hasPaymentType());
        assertFalse(criteria.hasOrderedFrom());
        assertFalse(criteria.hasOrderedTo());
        assertFalse(criteria.hasOrderFilled());
    }

    @Test
    void advancedFieldsAloneConstrainWithoutAQuickTerm() {
        OrderSearchCriteria criteria = new OrderSearchCriteria(
                "", "Unisports", "Magee", "CREDIT", null, null, Boolean.FALSE);

        assertFalse(criteria.isEmpty());
        // No quick term, but the advanced filters still constrain the result.
        assertFalse(criteria.hasQuickTerm());
        assertTrue(criteria.hasCustomer());
        assertEquals("%UNISPORTS%", criteria.customerPattern());
        assertTrue(criteria.hasSalesRep());
        assertEquals("%MAGEE%", criteria.salesRepPattern());
        assertTrue(criteria.hasPaymentType());
        assertEquals("CREDIT", criteria.paymentType());
        assertTrue(criteria.hasOrderFilled());
        assertFalse(criteria.orderFilled());
    }

    @Test
    void quickTermAndAdvancedFiltersAreAllActiveTogether() {
        // The QBE AND: a quick term plus advanced filters all constrain the result at once.
        OrderSearchCriteria criteria = new OrderSearchCriteria(
                "Magee", "Unisports", null, "CASH",
                LocalDate.of(1992, 8, 1), LocalDate.of(1992, 8, 31), Boolean.TRUE);

        assertTrue(criteria.hasQuickTerm());
        assertEquals("%MAGEE%", criteria.namePattern());
        assertTrue(criteria.hasCustomer());
        assertEquals("%UNISPORTS%", criteria.customerPattern());
        assertTrue(criteria.hasPaymentType());
        assertEquals("CASH", criteria.paymentType());
        assertTrue(criteria.hasOrderedFrom());
        assertTrue(criteria.hasOrderedTo());
        assertTrue(criteria.hasOrderFilled());
        assertTrue(criteria.orderFilled());
    }

    @Test
    void blankAdvancedFieldsAreNormalisedToNotSet() {
        OrderSearchCriteria criteria = new OrderSearchCriteria(
                "", "   ", "", "  ", null, null, null);

        assertTrue(criteria.isEmpty());
        assertFalse(criteria.hasCustomer());
        assertFalse(criteria.hasSalesRep());
        assertFalse(criteria.hasPaymentType());
        assertNull(criteria.customer());
        assertNull(criteria.salesRep());
        assertNull(criteria.paymentType());
    }

    @Test
    void orderedDateRangeIsInclusiveOnBothEnds() {
        OrderSearchCriteria criteria = new OrderSearchCriteria(
                "", null, null, null,
                LocalDate.of(1992, 9, 1), LocalDate.of(1992, 9, 3), null);

        // Both bounds match (inclusive); just outside either end does not.
        assertTrue(criteria.matchesOrderedDate(LocalDate.of(1992, 9, 1)));
        assertTrue(criteria.matchesOrderedDate(LocalDate.of(1992, 9, 3)));
        assertTrue(criteria.matchesOrderedDate(LocalDate.of(1992, 9, 2)));
        assertFalse(criteria.matchesOrderedDate(LocalDate.of(1992, 8, 31)));
        assertFalse(criteria.matchesOrderedDate(LocalDate.of(1992, 9, 4)));
    }

    @Test
    void anOpenEndedDateRangeBoundsOnlyTheSetEnd() {
        OrderSearchCriteria fromOnly = new OrderSearchCriteria(
                "", null, null, null, LocalDate.of(1992, 9, 1), null, null);
        assertTrue(fromOnly.matchesOrderedDate(LocalDate.of(1992, 9, 1)));
        assertTrue(fromOnly.matchesOrderedDate(LocalDate.of(2000, 1, 1)));
        assertFalse(fromOnly.matchesOrderedDate(LocalDate.of(1992, 8, 31)));

        OrderSearchCriteria toOnly = new OrderSearchCriteria(
                "", null, null, null, null, LocalDate.of(1992, 9, 1), null);
        assertTrue(toOnly.matchesOrderedDate(LocalDate.of(1992, 9, 1)));
        assertTrue(toOnly.matchesOrderedDate(LocalDate.of(1900, 1, 1)));
        assertFalse(toOnly.matchesOrderedDate(LocalDate.of(1992, 9, 2)));
    }
}
