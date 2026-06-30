package com.example.summit.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure domain test for the {@code TOTAL} summary — no Spring, no Vaadin, no DB. Guards that
 * {@link Order#total()} sums each line's {@link OrderLine#itemTotal()} (which itself uses
 * {@code quantity_shipped}), totals an empty order to zero, and treats unpriced lines as zero.
 */
class OrderTest {

    private static Order orderWith(List<OrderLine> lines) {
        return new Order(50001, 201, "Unisports", LocalDate.of(1992, 3, 16), null,
                12L, "Magee", "CREDIT", false, lines);
    }

    @Test
    void totalSumsTheLineItemTotals() {
        // 4 shipped * 50.00 = 200.00, plus 27 shipped * 13.00 = 351.00 -> 551.00.
        OrderLine boots = new OrderLine(1, 41010, "Ski Boots", new BigDecimal("50.00"), 10, 4);
        OrderLine pole = new OrderLine(2, 20108, "Ski Pole", new BigDecimal("13.00"), 27, 27);

        assertEquals(0, new BigDecimal("551.00").compareTo(orderWith(List.of(boots, pole)).total()));
    }

    @Test
    void totalFollowsQuantityShippedNotOrderedQuantity() {
        // Ordered 10 but only 4 shipped: the total follows what shipped (4 * 50.00 = 200.00),
        // not the ordered 10 (which would be 500.00).
        OrderLine boots = new OrderLine(1, 41010, "Ski Boots", new BigDecimal("50.00"), 10, 4);

        assertEquals(0, new BigDecimal("200.00").compareTo(orderWith(List.of(boots)).total()));
    }

    @Test
    void emptyOrderTotalsToZero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(orderWith(List.of()).total()));
    }

    @Test
    void unpricedLinesContributeZeroToTheTotal() {
        // A line with no price yet adds zero rather than throwing; the priced line still counts.
        OrderLine unpriced = new OrderLine(1, 41010, "Ski Boots", null, 5, 5);
        OrderLine pole = new OrderLine(2, 20108, "Ski Pole", new BigDecimal("13.00"), 27, 27);

        assertEquals(0, new BigDecimal("351.00").compareTo(orderWith(List.of(unpriced, pole)).total()));
    }
}
