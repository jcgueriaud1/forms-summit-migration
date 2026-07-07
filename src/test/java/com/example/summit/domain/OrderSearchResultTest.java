package com.example.summit.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The quick-search label is pure presentation logic on the domain type — unit-testable
 * with no Spring/DB, like the scaffold's {@code ServerInfoFormulaTest}.
 */
class OrderSearchResultTest {

    @Test
    void labelJoinsIdCustomerAndRep() {
        assertEquals("50001 — Unisports (Colin Magee)",
                new OrderSearchResult(50001L, "Unisports", "Colin Magee").label());
    }

    @Test
    void labelOmitsRepClauseWhenTheOrderHasNoSalesRep() {
        assertEquals("50003 — Unisports",
                new OrderSearchResult(50003L, "Unisports", null).label());
        assertEquals("50003 — Unisports",
                new OrderSearchResult(50003L, "Unisports", "  ").label());
    }
}
