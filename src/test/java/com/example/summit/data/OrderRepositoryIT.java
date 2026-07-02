package com.example.summit.data;

import com.example.summit.domain.Order;
import com.example.summit.domain.OrderLine;
import com.example.summit.domain.OrderSearchCriteria;
import com.example.summit.domain.OrderSearchCriteria.OrderStatus;
import com.example.summit.domain.OrderSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves {@code OrderRepository} against a real Summit Oracle (Testcontainers, via the
 * shared {@code SummitDbIT} harness). The headline assertion: {@code loadGraph} returns
 * the customer and sales-rep display names <em>in one shot</em> — the join dissolves the
 * two Forms POST-QUERY lookups, with no per-field round-trip.
 */
class OrderRepositoryIT extends SummitDbIT {

    private final OrderRepository repository = new OrderRepository(jdbcClient());

    @Test
    void loadGraphReturnsJoinedDisplayNamesInOneShot() {
        Order order = repository.loadGraph(50001).orElseThrow();

        assertEquals(50001L, order.id());
        assertEquals(201L, order.customerId());
        // The names arrive on the row from the join — not a per-field round-trip.
        assertEquals("Unisports", order.customerName());
        assertEquals(1L, order.salesRepId());
        assertEquals("Magee", order.salesRepName());
        assertEquals(LocalDate.of(1992, 8, 31), order.dateOrdered());
        assertEquals(LocalDate.of(1992, 9, 10), order.dateShipped());
        assertEquals("CASH", order.paymentType());
        assertTrue(order.orderFilled());
    }

    @Test
    void loadGraphReturnsTheFullOrderGraphInOneQueryPath() {
        // The headline of #40: one loadGraph call brings back the header AND its S_ITEM
        // lines, each line's product description resolved by the S_PRODUCT join — no
        // separate per-line lookup.
        Order order = repository.loadGraph(50001).orElseThrow();

        List<OrderLine> lines = order.lines();
        assertEquals(2, lines.size());

        // Ordered by item_id, as the grid shows them.
        OrderLine first = lines.get(0);
        assertEquals(1L, first.itemId());
        assertEquals(41010L, first.productId());
        assertEquals("Ski Boots", first.description()); // joined from S_PRODUCT.name
        assertEquals(0, new BigDecimal("50.00").compareTo(first.price()));
        assertEquals(5L, first.quantity());
        assertEquals(5L, first.quantityShipped());
        assertEquals(0, new BigDecimal("250.00").compareTo(first.itemTotal()));

        OrderLine second = lines.get(1);
        assertEquals(2L, second.itemId());
        assertEquals(20108L, second.productId());
        assertEquals("Ski Pole", second.description());
        assertEquals(27L, second.quantityShipped());
        assertEquals(0, new BigDecimal("351.00").compareTo(second.itemTotal()));
    }

    @Test
    void loadGraphReturnsAnEmptyLineListForAnOrderWithNoItems() {
        // 50003 has a header but no S_ITEM rows — the graph loads with an empty (never
        // null) line list, so the coordinated grid simply shows no rows.
        Order order = repository.loadGraph(50003).orElseThrow();

        assertTrue(order.lines().isEmpty());
    }

    @Test
    void loadGraphToleratesAnOrderWithNoSalesRep() {
        // 50003 has a null SALES_REP_ID — the LEFT JOIN keeps the row; the name is null.
        Order order = repository.loadGraph(50003).orElseThrow();

        assertEquals("Unisports", order.customerName());
        assertNull(order.salesRepId());
        assertNull(order.salesRepName());
        assertNull(order.dateShipped());
        assertFalse(order.orderFilled());
    }

    @Test
    void loadGraphReturnsEmptyForUnknownOrder() {
        Optional<Order> missing = repository.loadGraph(999999);
        assertTrue(missing.isEmpty());
    }

    // ---- nextItemId: the S_ITEM PRE-INSERT rule, proven per order against real data (issue #45) --

    @Test
    void nextItemIdIsMaxPlusOnePerOrder() {
        // 50001 has items 1 and 2 -> next is 3, scoped to that order's own line set.
        assertEquals(3L, repository.loadGraph(50001).orElseThrow().nextItemId());
    }

    @Test
    void nextItemIdOfAnOrderWithNoLinesIsOne() {
        // 50003 has no S_ITEM rows -> NVL(MAX(item_id),0)+1 = 1, the first line of the order.
        assertEquals(1L, repository.loadGraph(50003).orElseThrow().nextItemId());
    }

    @Test
    void findByCriteriaMatchesCustomerName() {
        List<OrderSummary> matches = repository.findByCriteria(new OrderSearchCriteria("womansport"));

        assertEquals(1, matches.size());
        assertEquals(50002L, matches.getFirst().id());
        assertEquals("Womansport", matches.getFirst().customerName());
        assertEquals("Giljum", matches.getFirst().salesRepName());
    }

    @Test
    void findByCriteriaMatchesSalesRepName() {
        List<OrderSummary> matches = repository.findByCriteria(new OrderSearchCriteria("Magee"));

        // Magee (rep 1) is the rep on order 50001; order 50003 has a null rep, so a
        // rep-name search correctly excludes it (the LEFT JOIN keeps no rep to match).
        assertEquals(List.of(50001L), matches.stream().map(OrderSummary::id).toList());
    }

    @Test
    void findByCriteriaMatchesOrderId() {
        List<OrderSummary> matches = repository.findByCriteria(new OrderSearchCriteria("50002"));

        assertEquals(1, matches.size());
        assertEquals(50002L, matches.getFirst().id());
    }

    @Test
    void findByCriteriaWithBlankTermReturnsAllOrders() {
        List<OrderSummary> matches = repository.findByCriteria(new OrderSearchCriteria("  "));

        assertEquals(List.of(50001L, 50002L, 50003L), matches.stream().map(OrderSummary::id).toList());
    }

    // ---- Advanced search: the QBE replacement (issue #43) --------------------------------------
    // The seed: 50001 (Unisports/Magee, ordered 1992-08-31, CASH, filled), 50002 (Womansport/Giljum,
    // 1992-09-01, CREDIT, unfilled), 50003 (Unisports/no rep, 1992-09-02, CASH, unfilled).

    @Test
    void findByCriteriaWithNoConstraintsAtAllReturnsAllOrders() {
        List<OrderSummary> matches = repository.findByCriteria(
                new OrderSearchCriteria(null, null, null, null, null, null, null));

        assertEquals(List.of(50001L, 50002L, 50003L), ids(matches));
    }

    @Test
    void findByCriteriaFiltersByPaymentType() {
        List<OrderSummary> matches = repository.findByCriteria(
                new OrderSearchCriteria("", null, null, "CREDIT", null, null, null));

        // Only 50002 is a CREDIT order.
        assertEquals(List.of(50002L), ids(matches));
    }

    @Test
    void findByCriteriaFiltersByStatus() {
        List<OrderSummary> filled = repository.findByCriteria(
                new OrderSearchCriteria("", null, null, null, null, null, OrderStatus.FILLED));
        assertEquals(List.of(50001L), ids(filled));

        List<OrderSummary> unfilled = repository.findByCriteria(
                new OrderSearchCriteria("", null, null, null, null, null, OrderStatus.UNFILLED));
        assertEquals(List.of(50002L, 50003L), ids(unfilled));
    }

    @Test
    void findByCriteriaFiltersByCustomerAndSalesRepContainsMatches() {
        // Customer "sport" (case-insensitive contains) matches Unisports (50001, 50003) and Womansport
        // (50002); adding the sales-rep "gil" narrows to Giljum's order alone (50002) — the AND.
        assertEquals(List.of(50001L, 50002L, 50003L), ids(repository.findByCriteria(
                new OrderSearchCriteria("", "sport", null, null, null, null, null))));
        assertEquals(List.of(50002L), ids(repository.findByCriteria(
                new OrderSearchCriteria("", "sport", "gil", null, null, null, null))));
    }

    @Test
    void findByCriteriaAppliesTheOrderedDateRangeInclusively() {
        // 50002 is ordered 1992-09-01, 50003 on 1992-09-02: a range with those exact bounds includes
        // both (the >= / <= bounds are inclusive), while 50001 (1992-08-31) falls outside the lower bound.
        List<OrderSummary> matches = repository.findByCriteria(new OrderSearchCriteria(
                "", null, null, null, LocalDate.of(1992, 9, 1), LocalDate.of(1992, 9, 2), null));

        assertEquals(List.of(50002L, 50003L), ids(matches));
    }

    @Test
    void findByCriteriaCombinesTheQuickTermWithAdvancedFiltersViaAnd() {
        // Quick "Unisports" matches 50001 and 50003; AND status=unfilled drops 50001 (filled), leaving
        // 50003 — the precise narrowing the advanced panel exists for.
        List<OrderSummary> matches = repository.findByCriteria(new OrderSearchCriteria(
                "Unisports", null, null, null, null, null, OrderStatus.UNFILLED));

        assertEquals(List.of(50003L), ids(matches));
    }

    private static List<Long> ids(List<OrderSummary> matches) {
        return matches.stream().map(OrderSummary::id).toList();
    }
}
