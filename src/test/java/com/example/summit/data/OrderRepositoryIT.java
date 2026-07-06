package com.example.summit.data;

import com.example.summit.domain.Order;
import com.example.summit.domain.OrderSummary;
import com.example.summit.domain.PaymentType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Repository/service integration against the seeded Summit Oracle (prior art
 * {@code SummitDbIT}). The headline assertion is that {@code loadGraph} returns the
 * joined display names in <em>one</em> read — the Forms {@code POST-QUERY} lookups
 * dissolved into a SQL join, no N+1.
 */
class OrderRepositoryIT extends SummitDbIT {

    private final OrderRepository repository = new OrderRepository(jdbcClient());

    @Test
    void loadGraphReturnsHeaderWithJoinedNamesInOneShot() {
        Order order = repository.loadGraph(50001).orElseThrow();

        assertEquals(50001, order.id());
        assertEquals(201, order.customerId());
        // Names arrive on the same read via the join (POST-QUERY dissolved).
        assertEquals("Unisports", order.customerName());
        assertEquals("Magee", order.salesRepName());
        assertEquals(1, order.salesRepId());
        assertEquals(LocalDate.of(1992, 8, 31), order.dateOrdered());
        assertEquals(LocalDate.of(1992, 9, 10), order.dateShipped());
        assertEquals(PaymentType.CASH, order.paymentType());
        assertTrue(order.orderFilled());
    }

    @Test
    void loadGraphMapsCreditAndUnshippedOrder() {
        Order order = repository.loadGraph(50002).orElseThrow();

        assertEquals("Womansport", order.customerName());
        assertEquals("Giljum", order.salesRepName());
        assertEquals(PaymentType.CREDIT, order.paymentType());
        assertFalse(order.orderFilled());
        assertEquals(LocalDate.of(1992, 9, 1), order.dateOrdered());
        assertEquals(null, order.dateShipped());
    }

    @Test
    void loadGraphReturnsEmptyForUnknownOrder() {
        assertTrue(repository.loadGraph(99999).isEmpty());
    }

    @Test
    void searchMatchesOnOrderId() {
        List<OrderSummary> hits = repository.search("50002");

        assertEquals(1, hits.size());
        assertEquals(50002, hits.get(0).id());
        assertEquals("Womansport", hits.get(0).customerName());
        assertEquals("Giljum", hits.get(0).salesRepName());
    }

    @Test
    void searchMatchesOnCustomerNameCaseInsensitive() {
        List<OrderSummary> hits = repository.search("unisports");

        // Customer 201 (Unisports) has orders 50001 and 50003.
        assertEquals(List.of(50001, 50003), hits.stream().map(OrderSummary::id).toList());
    }

    @Test
    void searchMatchesOnSalesRepName() {
        List<OrderSummary> hits = repository.search("Giljum");

        assertEquals(List.of(50002), hits.stream().map(OrderSummary::id).toList());
    }

    @Test
    void searchWithBlankTermReturnsAllOrders() {
        List<OrderSummary> hits = repository.search("  ");

        assertEquals(List.of(50001, 50002, 50003), hits.stream().map(OrderSummary::id).toList());
    }
}
