package com.example.summit.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for {@link Order#total()} — the {@code TOTAL} summary as a pure domain method
 * (ADR-0001). No DB, no Spring: the order-level total is the sum of the lines'
 * {@link OrderLine#itemTotal()}s, so it inherits their shipped-not-ordered truth and their
 * null-price safety. Prior art: {@code OrderLineTest}, {@code ServerInfoFormulaTest}.
 */
class OrderTest {

    @Test
    void totalSumsTheLineItemTotals() {
        // Order 50001's two lines: 5 @ 50.00 (250.00) + 27 @ 13.00 (351.00) -> 601.00.
        Order order = orderWith(
                new OrderLine(1, 41010, "Ski Boots", new BigDecimal("50.00"), 5, 5),
                new OrderLine(2, 20108, "Ski Pole", new BigDecimal("13.00"), 27, 27));

        assertEquals(0, new BigDecimal("601.00").compareTo(order.total()));
    }

    @Test
    void totalFollowsShippedNotOrderedQuantity() {
        // Ordered 10 but only 3 shipped: the total sums what shipped (3 @ 13.00).
        Order order = orderWith(new OrderLine(1, 20108, "Ski Pole", new BigDecimal("13.00"), 10, 3));

        assertEquals(0, new BigDecimal("39.00").compareTo(order.total()));
    }

    @Test
    void emptyOrderTotalsToZero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(orderWith().total()));
    }

    @Test
    void aNullPricedLineContributesNothing() {
        // A half-entered line (null price) adds zero rather than throwing; the priced line still counts.
        Order order = orderWith(
                new OrderLine(1, 30421, "Pro Ski Boot", null, 4, 4),
                new OrderLine(2, 41010, "Ski Boots", new BigDecimal("50.00"), 2, 2));

        assertEquals(0, new BigDecimal("100.00").compareTo(order.total()));
    }

    private static Order orderWith(OrderLine... lines) {
        return new Order(50001, 201, "Unisports", LocalDate.of(1992, 8, 31), null,
                1L, "Magee", "CASH", true, List.of(lines));
    }
}
