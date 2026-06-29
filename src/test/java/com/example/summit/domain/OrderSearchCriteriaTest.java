package com.example.summit.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
