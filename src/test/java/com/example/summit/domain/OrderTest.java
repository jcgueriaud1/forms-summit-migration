package com.example.summit.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for {@link Order}'s pure domain methods (ADR-0001) — no DB, no Spring. The
 * {@code TOTAL} summary sums the lines' {@link OrderLine#itemTotal()}s (inheriting their
 * shipped-not-ordered truth and null-price safety); the {@code S_ITEM} {@code PRE-INSERT}
 * next-id rule is {@code NVL(MAX(item_id),0)+1} per order; and {@code hasLineItems} is the
 * Non-Isolated cascade-delete guard's predicate (issue #45). Prior art: {@code OrderLineTest},
 * {@code ServerInfoFormulaTest}.
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

    // ---- nextItemId: the S_ITEM PRE-INSERT rule NVL(MAX(item_id),0)+1, per order (issue #45) ----

    @Test
    void nextItemIdIsMaxPlusOne() {
        OrderLine one = new OrderLine(1, 41010, "Ski Boots", new BigDecimal("50.00"), 5, 5);
        OrderLine two = new OrderLine(2, 20108, "Ski Pole", new BigDecimal("13.00"), 27, 27);

        assertEquals(3L, orderWith(one, two).nextItemId());
    }

    @Test
    void nextItemIdFollowsTheMaxNotTheCount() {
        // A gap below the max (item 1 removed) still yields max+1, not count+1 — the id never reuses.
        OrderLine two = new OrderLine(2, 20108, "Ski Pole", new BigDecimal("13.00"), 27, 27);
        OrderLine five = new OrderLine(5, 41010, "Ski Boots", new BigDecimal("50.00"), 5, 5);

        assertEquals(6L, orderWith(two, five).nextItemId());
    }

    @Test
    void nextItemIdOfAnEmptyOrderIsOne() {
        // NVL(MAX,0)+1 over no lines = 1 — the first line of a fresh order.
        assertEquals(1L, orderWith().nextItemId());
    }

    // ---- hasLineItems: the Non-Isolated cascade-delete guard predicate (issue #45) --------------

    @Test
    void hasLineItemsIsTrueWhenLinesExist() {
        assertTrue(orderWith(new OrderLine(1, 41010, "Ski Boots", new BigDecimal("50.00"), 5, 5)).hasLineItems());
    }

    @Test
    void hasLineItemsIsFalseForAnEmptyOrder() {
        assertFalse(orderWith().hasLineItems());
    }

    private static Order orderWith(OrderLine... lines) {
        return new Order(50001, 201, "Unisports", LocalDate.of(1992, 8, 31), null,
                1L, "Magee", "CASH", true, List.of(lines));
    }
}
