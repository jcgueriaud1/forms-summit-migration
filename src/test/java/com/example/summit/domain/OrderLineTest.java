package com.example.summit.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure domain test for the {@code ITEM_TOTAL} formula — no Spring, no DB. Guards the one
 * subtlety the Forms spec calls out: the total multiplies {@code quantity_shipped}, not the
 * ordered {@code quantity}.
 */
class OrderLineTest {

    @Test
    void itemTotalMultipliesQuantityShippedByPrice() {
        // Ordered 27 but shipped 27 at 13.00 -> 351.00.
        OrderLine line = new OrderLine(2, 20108, "Ski Pole", new BigDecimal("13.00"), 27, 27);

        assertEquals(0, new BigDecimal("351.00").compareTo(line.itemTotal()));
    }

    @Test
    void itemTotalUsesQuantityShippedNotOrderedQuantity() {
        // Ordered 10 but only 4 shipped: the total follows what shipped (4 * 50.00 = 200.00),
        // not the ordered 10 (which would be 500.00).
        OrderLine line = new OrderLine(1, 41010, "Ski Boots", new BigDecimal("50.00"), 10, 4);

        assertEquals(0, new BigDecimal("200.00").compareTo(line.itemTotal()));
    }

    @Test
    void itemTotalIsZeroForAnUnpricedLine() {
        // A line with no price yet totals to zero rather than throwing — the grid stays renderable.
        OrderLine line = new OrderLine(1, 41010, "Ski Boots", null, 5, 5);

        assertEquals(0, BigDecimal.ZERO.compareTo(line.itemTotal()));
    }
}
