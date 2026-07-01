package com.example.summit.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit test for {@link OrderLine} — the {@code ITEM_TOTAL} formula and the {@code KEY-DUPREC}
 * clear-list as pure domain methods (ADR-0001). No DB, no Spring: {@code quantity_shipped * price}
 * and {@link OrderLine#duplicateForInsert()}, computed and asserted in isolation.
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

    // ---- duplicateForInsert: the KEY-DUPREC clear-list (issue #45) -------------------------------

    @Test
    void duplicateForInsertClearsTheKeyDuprecFieldsButKeepsQuantities() {
        OrderLine original = new OrderLine(2, 41010, "Ski Boots", new BigDecimal("50.00"), 5, 3);

        OrderLine copy = original.duplicateForInsert();

        // Cleared so the copy commits as an insert, not a collision: item_id, product_id,
        // description and price (exactly the fields the Forms KEY-DUPREC body nulls).
        assertEquals(0L, copy.itemId());
        assertEquals(0L, copy.productId());
        assertNull(copy.description());
        assertNull(copy.price());
        // Carried over — the quantities are what the clerk is duplicating.
        assertEquals(5L, copy.quantity());
        assertEquals(3L, copy.quantityShipped());
    }
}
