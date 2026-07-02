package com.example.summit.domain;

import com.example.summit.domain.OrderSearchCriteria.OrderStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code OrderSearchCriteria} is pure (no Spring, no DB), so the quick-term + advanced-filter →
 * predicate translation is unit-testable in isolation — the same property as
 * {@code ServerInfoFormulaTest}. These cover the issue #43 acceptance criteria: the quick term alone,
 * the advanced fields alone, the two combined (AND), empty-matches-all, and the inclusive date-range
 * bounds.
 */
class OrderSearchCriteriaTest {

    // ---- Quick term alone ----------------------------------------------------------------------

    @Test
    void blankOrNullTermMatchesEveryOrder() {
        assertTrue(new OrderSearchCriteria("").isEmpty());
        assertTrue(new OrderSearchCriteria("   ").isEmpty());
        assertTrue(new OrderSearchCriteria(null).isEmpty());
    }

    @Test
    void termIsTrimmed() {
        assertEquals("Magee", new OrderSearchCriteria("  Magee  ").quickTerm());
    }

    @Test
    void allDigitTermCanMatchOrderId() {
        OrderSearchCriteria criteria = new OrderSearchCriteria("50002");
        assertTrue(criteria.hasQuickTerm());
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
        // All digits, but too large for a long — cannot be an order id; the name match
        // still applies, so the caller degrades rather than throwing.
        OrderSearchCriteria criteria = new OrderSearchCriteria("999999999999999999999999");
        assertTrue(criteria.matchesOrderId());
        assertTrue(criteria.orderId().isEmpty());
    }

    @Test
    void namePatternIsAnUppercasedContainsPattern() {
        assertEquals("%MAGEE%", new OrderSearchCriteria("magee").namePattern());
        assertEquals("%UNISPORTS%", new OrderSearchCriteria("Unisports").namePattern());
    }

    @Test
    void aQuickTermAloneSetsNoAdvancedFilters() {
        OrderSearchCriteria criteria = new OrderSearchCriteria("Womansport");
        assertFalse(criteria.isEmpty());
        assertTrue(criteria.hasQuickTerm());
        assertTrue(criteria.customerPattern().isEmpty());
        assertTrue(criteria.salesRepPattern().isEmpty());
        assertFalse(criteria.hasPaymentType());
        assertNull(criteria.orderedFrom());
        assertNull(criteria.orderedTo());
        assertNull(criteria.status());
    }

    // ---- Advanced fields alone -----------------------------------------------------------------

    @Test
    void advancedFieldsAloneAreNotEmptyAndCarryNoQuickTerm() {
        OrderSearchCriteria criteria = new OrderSearchCriteria(
                "", "Womansport", "Giljum", "CREDIT",
                LocalDate.of(1992, 9, 1), LocalDate.of(1992, 9, 30), OrderStatus.UNFILLED);

        // No quick term, so no OR group — but the criteria are not empty: each advanced filter constrains.
        assertFalse(criteria.hasQuickTerm());
        assertFalse(criteria.isEmpty());
    }

    @Test
    void advancedCustomerAndSalesRepAreUppercasedContainsPatterns() {
        OrderSearchCriteria criteria = new OrderSearchCriteria(
                "", "womansport", "giljum", null, null, null, null);

        assertEquals("%WOMANSPORT%", criteria.customerPattern().orElseThrow());
        assertEquals("%GILJUM%", criteria.salesRepPattern().orElseThrow());
    }

    @Test
    void blankAdvancedTextFieldsAreUnset() {
        OrderSearchCriteria criteria = new OrderSearchCriteria(
                "", "   ", "", "  ", null, null, null);

        assertTrue(criteria.customerPattern().isEmpty());
        assertTrue(criteria.salesRepPattern().isEmpty());
        assertFalse(criteria.hasPaymentType());
    }

    @Test
    void paymentTypeAndStatusAreCarriedThrough() {
        OrderSearchCriteria criteria = new OrderSearchCriteria(
                "", null, null, "CASH", null, null, OrderStatus.FILLED);

        assertTrue(criteria.hasPaymentType());
        assertEquals("CASH", criteria.paymentType());
        assertEquals(OrderStatus.FILLED, criteria.status());
        // The status maps to the S_ORD.ORDER_FILLED Y/N flag the repository matches on.
        assertEquals("Y", criteria.status().flag());
        assertEquals("N", OrderStatus.UNFILLED.flag());
    }

    // ---- Combined (AND) ------------------------------------------------------------------------

    @Test
    void quickTermAndAdvancedFiltersCoexistForAnAndedSearch() {
        // "Unisports" (quick) narrowed to unfilled orders from a date onward — the quick group AND the
        // advanced filters, the precise-narrowing case the advanced panel exists for.
        OrderSearchCriteria criteria = new OrderSearchCriteria(
                "Unisports", null, null, null,
                LocalDate.of(1992, 9, 1), null, OrderStatus.UNFILLED);

        assertFalse(criteria.isEmpty());
        assertTrue(criteria.hasQuickTerm());
        assertEquals("%UNISPORTS%", criteria.namePattern());
        assertEquals(LocalDate.of(1992, 9, 1), criteria.orderedFrom());
        assertEquals(OrderStatus.UNFILLED, criteria.status());
    }

    // ---- Empty matches all ---------------------------------------------------------------------

    @Test
    void criteriaWithNothingSetAtAllMatchEveryOrder() {
        OrderSearchCriteria criteria = new OrderSearchCriteria(
                null, null, null, null, null, null, null);

        assertTrue(criteria.isEmpty());
        assertFalse(criteria.hasQuickTerm());
    }

    // ---- Inclusive date-range bounds -----------------------------------------------------------

    @Test
    void dateRangeCapturesBothBoundsForAnInclusiveMatch() {
        // The bounds are carried verbatim; the repository renders them as `>= from AND <= to`, so the
        // from and to dates themselves are inside the range (proven end-to-end in OrderRepositoryIT).
        LocalDate from = LocalDate.of(1992, 9, 1);
        LocalDate to = LocalDate.of(1992, 9, 30);
        OrderSearchCriteria criteria = new OrderSearchCriteria(
                "", null, null, null, from, to, null);

        assertEquals(from, criteria.orderedFrom());
        assertEquals(to, criteria.orderedTo());
        assertFalse(criteria.isEmpty());
    }

    @Test
    void aSingleDayRangeIsTheDegenerateInclusiveWindow() {
        // from == to is a one-day window: an order on exactly that day is inside it (from <= d <= to).
        LocalDate day = LocalDate.of(1992, 9, 1);
        OrderSearchCriteria criteria = new OrderSearchCriteria(
                "", null, null, null, day, day, null);

        assertEquals(day, criteria.orderedFrom());
        assertEquals(day, criteria.orderedTo());
    }

    @Test
    void anOpenEndedRangeSetsOnlyOneBound() {
        OrderSearchCriteria from = new OrderSearchCriteria(
                "", null, null, null, LocalDate.of(1992, 9, 1), null, null);
        assertEquals(LocalDate.of(1992, 9, 1), from.orderedFrom());
        assertNull(from.orderedTo());

        OrderSearchCriteria to = new OrderSearchCriteria(
                "", null, null, null, null, LocalDate.of(1992, 9, 30), null);
        assertNull(to.orderedFrom());
        assertEquals(LocalDate.of(1992, 9, 30), to.orderedTo());
    }
}
