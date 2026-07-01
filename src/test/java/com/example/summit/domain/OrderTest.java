package com.example.summit.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure domain test for the {@code TOTAL} summary — no Spring, no Vaadin, no DB. Guards that
 * {@link Order#total()} sums each line's {@link OrderLine#itemTotal()} (which itself uses
 * {@code quantity_shipped}), totals an empty order to zero, and treats unpriced lines as zero.
 * Also guards the {@code S_ITEM} {@code PRE-INSERT} next-id rule and the Non-Isolated delete
 * guard's predicate (issue #16).
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

    // ---- nextItemId: the S_ITEM PRE-INSERT rule NVL(MAX(item_id),0)+1, per order (issue #16) ----

    @Test
    void nextItemIdIsMaxPlusOne() {
        OrderLine one = new OrderLine(1, 41010, "Ski Boots", new BigDecimal("50.00"), 5, 5);
        OrderLine two = new OrderLine(2, 20108, "Ski Pole", new BigDecimal("13.00"), 27, 27);

        assertEquals(3L, orderWith(List.of(one, two)).nextItemId());
    }

    @Test
    void nextItemIdFollowsTheMaxNotTheCount() {
        // A gap below the max (item 1 removed) still yields max+1, not count+1 — the id never reuses.
        OrderLine two = new OrderLine(2, 20108, "Ski Pole", new BigDecimal("13.00"), 27, 27);
        OrderLine five = new OrderLine(5, 41010, "Ski Boots", new BigDecimal("50.00"), 5, 5);

        assertEquals(6L, orderWith(List.of(two, five)).nextItemId());
    }

    @Test
    void nextItemIdOfAnEmptyOrderIsOne() {
        // NVL(MAX,0)+1 over no lines = 1 — the first line of a fresh order.
        assertEquals(1L, orderWith(List.of()).nextItemId());
    }

    // ---- hasLineItems: the Non-Isolated cascade-delete guard predicate (issue #16) --------------

    @Test
    void hasLineItemsIsTrueWhenLinesExist() {
        OrderLine one = new OrderLine(1, 41010, "Ski Boots", new BigDecimal("50.00"), 5, 5);

        assertTrue(orderWith(List.of(one)).hasLineItems());
    }

    @Test
    void hasLineItemsIsFalseForAnEmptyOrder() {
        assertFalse(orderWith(List.of()).hasLineItems());
    }

    // ---- shipDateValid: the S_ORD WHEN-VALIDATE-RECORD ship-date guard (issue #18) --------------
    // Forms: IF :date_shipped < :date_ordered THEN RAISE. Only an *earlier* ship date is rejected.

    @Test
    void shipDateAfterOrderDateIsValid() {
        assertTrue(Order.shipDateValid(LocalDate.of(1992, 8, 31), LocalDate.of(1992, 9, 10)));
    }

    @Test
    void equalShipAndOrderDatesAreValid() {
        // date_shipped = date_ordered is not "before", so the guard allows it.
        LocalDate sameDay = LocalDate.of(1992, 9, 1);
        assertTrue(Order.shipDateValid(sameDay, sameDay));
    }

    @Test
    void aNullShipDateIsValid() {
        // Not yet shipped: in Oracle NULL < date_ordered is unknown, so the guard never fired.
        assertTrue(Order.shipDateValid(LocalDate.of(1992, 9, 1), null));
    }

    @Test
    void shipDateBeforeOrderDateIsRejected() {
        assertFalse(Order.shipDateValid(LocalDate.of(1992, 9, 10), LocalDate.of(1992, 8, 31)));
    }

    @Test
    void instanceShipDateValidUsesTheOrdersOwnDates() {
        // The instance check reads the order's own date_ordered / date_shipped.
        OrderLine one = new OrderLine(1, 41010, "Ski Boots", new BigDecimal("50.00"), 5, 5);
        Order shippedBeforeOrdered = new Order(50001, 201, "Unisports",
                LocalDate.of(1992, 9, 10), LocalDate.of(1992, 8, 31),
                12L, "Magee", "CASH", false, List.of(one));

        assertFalse(shippedBeforeOrdered.shipDateValid());
        assertTrue(orderWith(List.of(one)).shipDateValid()); // orderWith has a null ship date
    }
}
