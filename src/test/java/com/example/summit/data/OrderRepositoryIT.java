package com.example.summit.data;

import com.example.summit.domain.Order;
import com.example.summit.domain.OrderSearchResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Repository integration test against a real seeded Summit Oracle (Testcontainers via
 * {@link SummitDbIT}). It proves the key promise of this slice: {@code loadGraph}
 * returns the customer name and sales-rep name <em>on the order row itself</em>, in a
 * single joined read — the Forms {@code S_ORD.POST-QUERY} lookups dissolved, no N+1.
 */
class OrderRepositoryIT extends SummitDbIT {

    private final OrderRepository repository = new OrderRepository(jdbcClient());

    @Test
    void loadGraphReturnsJoinedCustomerAndSalesRepNamesInOneRead() {
        Order order = repository.loadGraph(50001).orElseThrow();

        assertEquals(50001L, order.id());
        assertEquals(201L, order.customerId());
        assertEquals("Unisports", order.customerName());       // joined S_CUSTOMER.name
        assertEquals(1L, order.salesRepId());
        assertEquals("Colin Magee", order.salesRepName());     // joined S_EMP first+last
        assertEquals(LocalDate.of(1992, 8, 31), order.dateOrdered());
        assertEquals(LocalDate.of(1992, 9, 10), order.dateShipped());
        assertEquals("CASH", order.paymentType());
        assertTrue(order.orderFilled());                       // ORDER_FILLED = 'Y'
    }

    @Test
    void loadGraphToleratesAnOrderWithoutASalesRep() {
        // 50003 has SALES_REP_ID null and DATE_SHIPPED null — the left join must not drop it.
        Order order = repository.loadGraph(50003).orElseThrow();

        assertEquals("Unisports", order.customerName());
        assertNull(order.salesRepId());
        assertNull(order.salesRepName());
        assertNull(order.dateShipped());
        assertFalse(order.orderFilled());                      // ORDER_FILLED = 'N'
    }

    @Test
    void loadGraphIsEmptyForAnUnknownOrder() {
        assertTrue(repository.loadGraph(99999).isEmpty());
    }

    @Test
    void searchMatchesOnCustomerName() {
        List<OrderSearchResult> hits = repository.search("unisport");

        assertFalse(hits.isEmpty());
        assertTrue(hits.stream().allMatch(h -> "Unisports".equals(h.customerName())));
        assertTrue(hits.stream().anyMatch(h -> h.id() == 50001L));
    }

    @Test
    void searchMatchesOnSalesRepName() {
        List<OrderSearchResult> hits = repository.search("giljum");

        // Henry Giljum is the rep on order 50002 only.
        assertEquals(List.of(50002L), hits.stream().map(OrderSearchResult::id).toList());
        assertEquals("Henry Giljum", hits.get(0).salesRepName());
    }

    @Test
    void searchMatchesOnOrderId() {
        List<OrderSearchResult> hits = repository.search("50002");

        assertEquals(List.of(50002L), hits.stream().map(OrderSearchResult::id).toList());
    }

    @Test
    void searchReturnsNothingForABlankTerm() {
        assertTrue(repository.search("   ").isEmpty());
    }
}
