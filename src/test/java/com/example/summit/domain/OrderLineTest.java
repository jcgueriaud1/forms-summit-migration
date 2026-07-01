package com.example.summit.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for {@link OrderLine} — the {@code ITEM_TOTAL} formula as a pure domain
 * method (ADR-0001). No DB, no Spring: {@code quantity_shipped * price}, computed and
 * asserted in isolation.
 */
class OrderLineTest {

    @Test
    void itemTotalIsShippedQuantityTimesPrice() {
        // 41010 on order 50001: 5 @ 50.00 -> 250.00.
        OrderLine line = new OrderLine(1, 41010, "Ski Boots", new BigDecimal("50.00"), 5, 5);

        assertEquals(0, new BigDecimal("250.00").compareTo(line.itemTotal()));
    }

    @Test
    void itemTotalUsesShippedNotOrderedQuantity() {
        // Ordered 10 but only 3 shipped: the total follows what shipped (3 @ 13.00).
        OrderLine line = new OrderLine(2, 20108, "Ski Pole", new BigDecimal("13.00"), 10, 3);

        assertEquals(0, new BigDecimal("39.00").compareTo(line.itemTotal()));
    }

    @Test
    void itemTotalTreatsNullPriceAsZero() {
        OrderLine line = new OrderLine(3, 30421, "Pro Ski Boot", null, 4, 4);

        assertEquals(0, BigDecimal.ZERO.compareTo(line.itemTotal()));
    }
}
