package com.example.summit.data;

import com.example.summit.domain.Order;
import com.example.summit.domain.OrderSearchCriteria;
import com.example.summit.domain.OrderSummary;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves {@code OrderRepository} against a real Summit Oracle (Testcontainers). The
 * headline assertion: {@code loadGraph} returns the customer and sales-rep display
 * names <em>in one shot</em> — the join dissolves the Forms POST-QUERY lookups.
 */
class OrderRepositoryIntegrationTest extends SummitDbIntegrationTest {

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
