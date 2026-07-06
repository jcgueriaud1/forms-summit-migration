package com.example.summit.data;

import com.example.summit.domain.Order;
import com.example.summit.domain.OrderSummary;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link OrderRepository} against a real Summit Oracle (Testcontainers,
 * {@code extends SummitDbIT}). It proves the two reads behind the ORDERS tracer bullet
 * (issue #85):
 * <ul>
 *   <li>{@code loadGraph} returns the customer and sales-rep names on <strong>one</strong>
 *       read — the SQL join dissolves the Forms {@code POST-QUERY} lookups (no N+1);</li>
 *   <li>quick search matches on order id, customer name, and sales-rep name.</li>
 * </ul>
 * Values come from the seed data in {@code db/summit-slice.sql}.
 */
class OrderRepositoryIT extends SummitDbIT {

    private final OrderRepository repository = new OrderRepository(jdbcClient());

    @Test
    void loadGraphReturnsJoinedNamesInOneRead() {
        Order order = repository.loadGraph(50001).orElseThrow();

        // The names arrive with the row — proof the join replaced the POST-QUERY lookups.
        assertThat(order.customerName()).isEqualTo("Unisports");
        assertThat(order.salesRepName()).isEqualTo("Magee");
        assertThat(order.customerId()).isEqualTo(201);
        assertThat(order.dateOrdered()).isEqualTo(LocalDate.of(1992, 8, 31));
        assertThat(order.dateShipped()).isEqualTo(LocalDate.of(1992, 9, 10));
        assertThat(order.paymentType()).isEqualTo("CASH");
        assertThat(order.orderFilled()).isTrue();
    }

    @Test
    void loadGraphLeftJoinsAbsentSalesRep() {
        // Order 50003 has a NULL sales_rep_id — a LEFT JOIN must still return the order
        // (with a null rep name), where an inner join would silently drop it.
        Order order = repository.loadGraph(50003).orElseThrow();

        assertThat(order.customerName()).isEqualTo("Unisports");
        assertThat(order.salesRepId()).isNull();
        assertThat(order.salesRepName()).isNull();
    }

    @Test
    void loadGraphIsEmptyForUnknownOrder() {
        assertThat(repository.loadGraph(99999)).isEmpty();
    }

    @Test
    void searchMatchesByOrderId() {
        List<OrderSummary> matches = repository.search("50002");

        assertThat(matches).extracting(OrderSummary::id).containsExactly(50002L);
    }

    @Test
    void searchMatchesByCustomerName() {
        List<OrderSummary> matches = repository.search("womansport");

        assertThat(matches).extracting(OrderSummary::id).containsExactly(50002L);
        assertThat(matches).first().extracting(OrderSummary::salesRepName).isEqualTo("Giljum");
    }

    @Test
    void searchMatchesBySalesRepName() {
        // Rep Magee (id 1) is the rep on order 50001 only (50003's rep is NULL).
        List<OrderSummary> matches = repository.search("Magee");

        assertThat(matches).extracting(OrderSummary::id).containsExactly(50001L);
    }
}
