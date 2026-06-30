package com.example.summit.data;

import com.example.summit.domain.Order;
import com.example.summit.domain.OrderLine;
import com.example.summit.domain.OrderSearchCriteria;
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
 * Proves {@code OrderRepository} against a real Summit Oracle (Testcontainers). The
 * headline assertions: {@code loadGraph} returns the customer and sales-rep display
 * names <em>in one shot</em>, and the order's {@code S_ITEM} line items — each with its
 * product description joined — as part of the <em>same</em> query path. The joins
 * dissolve every Forms POST-QUERY lookup.
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
    void loadGraphToleratesAnOrderWithNoSalesRep() {
        // 50003 has a null SALES_REP_ID — the LEFT JOIN keeps the row, name is null.
        Order order = repository.loadGraph(50003).orElseThrow();

        assertEquals("Unisports", order.customerName());
        assertNull(order.salesRepId());
        assertNull(order.salesRepName());
        assertNull(order.dateShipped());
        assertFalse(order.orderFilled());
    }

    @Test
    void loadGraphReturnsTheFullOrderGraphInOneQueryPath() {
        // The whole aggregate — header + S_ITEM lines + each line's joined product
        // description — comes back from the single loadGraph query path (no N+1).
        Order order = repository.loadGraph(50001).orElseThrow();

        // Header still resolves as before.
        assertEquals(50001L, order.id());
        assertEquals("Unisports", order.customerName());
        assertEquals("Magee", order.salesRepName());

        // Lines arrive ordered by item_id, the product description joined onto each row.
        assertEquals(2, order.lines().size());

        OrderLine first = order.lines().get(0);
        assertEquals(1L, first.itemId());
        assertEquals(41010L, first.productId());
        assertEquals("Ski Boots", first.productName());
        assertEquals(0, new BigDecimal("50.00").compareTo(first.price()));
        assertEquals(5L, first.quantity());
        assertEquals(5L, first.quantityShipped());
        // ITEM_TOTAL formula: quantity_shipped * price.
        assertEquals(0, new BigDecimal("250.00").compareTo(first.itemTotal()));

        OrderLine second = order.lines().get(1);
        assertEquals(2L, second.itemId());
        assertEquals(20108L, second.productId());
        assertEquals("Ski Pole", second.productName());
        assertEquals(27L, second.quantityShipped());
        assertEquals(0, new BigDecimal("351.00").compareTo(second.itemTotal()));

        // The line totals reconcile to the order's stored S_ORD.total (601.00).
        BigDecimal lineSum = order.lines().stream()
                .map(OrderLine::itemTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("601.00").compareTo(lineSum));
    }

    @Test
    void loadGraphReturnsAnEmptyLineListForAnOrderWithNoItems() {
        // 50003 has no S_ITEM rows — the LEFT JOIN keeps the header, lines is empty.
        Order order = repository.loadGraph(50003).orElseThrow();

        assertEquals(50003L, order.id());
        assertTrue(order.lines().isEmpty());
    }

    @Test
    void loadGraphReturnsEmptyForUnknownOrder() {
        Optional<Order> missing = repository.loadGraph(999999);
        assertTrue(missing.isEmpty());
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

        // Magee (rep 1) is the rep on order 50001. Order 50003 has a null sales rep, so
        // a rep-name search correctly excludes it (the LEFT JOIN keeps no rep to match).
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
}
