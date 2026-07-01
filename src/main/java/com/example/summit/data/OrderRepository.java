package com.example.summit.data;

import com.example.summit.domain.Order;
import com.example.summit.domain.OrderLine;
import com.example.summit.domain.OrderSearchCriteria;
import com.example.summit.domain.OrderSummary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data layer for the {@code S_ORD} master — the persistence boundary (Spring
 * {@link JdbcClient}). All SQL lives here; the view, presenter, and service hold none.
 *
 * <p>Table names are <strong>unqualified</strong> deliberately: the app connects as
 * the schema owner ({@code summit}), so unqualified names resolve to its own objects,
 * and the integration harness runs as its own user over the same slice tables — the
 * identical SQL resolves in both.
 */
@Repository
public class OrderRepository {

    /** Autocomplete cap — one order is loaded from the suggestions, so a wide net is unnecessary. */
    private static final int MAX_SUGGESTIONS = 50;

    private final JdbcClient jdbcClient;

    public OrderRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Loads one order's whole graph — the header <em>and</em> its {@code S_ITEM} line
     * items — as the unit of load, so the view's header form and line-items grid stay
     * coordinated on the same order ({@code S_ORD_S_ITEM}).
     *
     * <p>Every display name arrives resolved by a join, never a per-row lookup: the
     * header's customer and sales-rep names dissolve the two Forms POST-QUERY header
     * lookups, and each line's product {@code description} dissolves the per-line
     * POST-QUERY — so there is no N+1 across the graph. The lines are fetched only when
     * the header exists; a missing order returns empty without a second round-trip.
     */
    public Optional<Order> loadGraph(long orderId) {
        Optional<Order> header = loadHeader(orderId);
        return header.map(order -> withLines(order, loadLines(orderId)));
    }

    /**
     * Loads the header row. {@code S_CUSTOMER} joins as an inner join
     * ({@code CUSTOMER_ID} is mandatory); {@code S_EMP} joins left, because
     * {@code SALES_REP_ID} is nullable — an order without a rep still loads, with a
     * {@code null} rep id and name. The lines are attached by {@link #loadGraph}.
     */
    private Optional<Order> loadHeader(long orderId) {
        return jdbcClient
                .sql("""
                        SELECT o.id, o.customer_id, c.name AS customer_name,
                               o.date_ordered, o.date_shipped,
                               o.sales_rep_id, e.last_name AS sales_rep_name,
                               o.payment_type, o.order_filled
                        FROM s_ord o
                        JOIN s_customer c ON c.id = o.customer_id
                        LEFT JOIN s_emp e ON e.id = o.sales_rep_id
                        WHERE o.id = :id
                        """)
                .param("id", orderId)
                .query((rs, rowNum) -> new Order(
                        rs.getLong("id"),
                        rs.getLong("customer_id"),
                        rs.getString("customer_name"),
                        rs.getObject("date_ordered", LocalDate.class),
                        rs.getObject("date_shipped", LocalDate.class),
                        rs.getObject("sales_rep_id", Long.class),
                        rs.getString("sales_rep_name"),
                        rs.getString("payment_type"),
                        "Y".equals(rs.getString("order_filled")),
                        List.of()))
                .optional();
    }

    /**
     * Loads the order's {@code S_ITEM} lines, joining {@code S_PRODUCT} so each line's
     * product {@code description} (the product name) arrives with the row — the join that
     * dissolves the per-line Forms POST-QUERY, ordered by {@code item_id} as the grid
     * shows them.
     */
    private List<OrderLine> loadLines(long orderId) {
        return jdbcClient
                .sql("""
                        SELECT i.item_id, i.product_id, p.name AS description,
                               i.price, i.quantity, i.quantity_shipped
                        FROM s_item i
                        JOIN s_product p ON p.id = i.product_id
                        WHERE i.ord_id = :id
                        ORDER BY i.item_id
                        """)
                .param("id", orderId)
                .query((rs, rowNum) -> new OrderLine(
                        rs.getLong("item_id"),
                        rs.getLong("product_id"),
                        rs.getString("description"),
                        rs.getObject("price", BigDecimal.class),
                        rs.getLong("quantity"),
                        rs.getLong("quantity_shipped")))
                .list();
    }

    private static Order withLines(Order header, List<OrderLine> lines) {
        return new Order(
                header.id(), header.customerId(), header.customerName(),
                header.dateOrdered(), header.dateShipped(),
                header.salesRepId(), header.salesRepName(),
                header.paymentType(), header.orderFilled(),
                lines);
    }

    /**
     * Returns the orders matching the quick-search criteria — the suggestions the
     * autocomplete offers. {@link OrderSearchCriteria} decides what to match; this
     * method renders that decision as SQL. Customer and sales-rep names are joined so
     * the term can match them and each suggestion can display them, capped at
     * {@value #MAX_SUGGESTIONS} rows.
     */
    public List<OrderSummary> findByCriteria(OrderSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder("""
                SELECT o.id, c.name AS customer_name, e.last_name AS sales_rep_name, o.date_ordered
                FROM s_ord o
                JOIN s_customer c ON c.id = o.customer_id
                LEFT JOIN s_emp e ON e.id = o.sales_rep_id
                """);

        boolean matchOrderId = criteria.orderId().isPresent();
        if (!criteria.isEmpty()) {
            List<String> predicates = new ArrayList<>(List.of(
                    "UPPER(c.name) LIKE :pattern",
                    "UPPER(e.last_name) LIKE :pattern"));
            if (matchOrderId) {
                predicates.add("o.id = :orderId");
            }
            sql.append("WHERE ").append(String.join(" OR ", predicates)).append('\n');
        }
        sql.append("ORDER BY o.id FETCH FIRST ").append(MAX_SUGGESTIONS).append(" ROWS ONLY");

        JdbcClient.StatementSpec spec = jdbcClient.sql(sql.toString());
        if (!criteria.isEmpty()) {
            spec = spec.param("pattern", criteria.namePattern());
            if (matchOrderId) {
                spec = spec.param("orderId", criteria.orderId().getAsLong());
            }
        }
        return spec
                .query((rs, rowNum) -> new OrderSummary(
                        rs.getLong("id"),
                        rs.getString("customer_name"),
                        rs.getString("sales_rep_name"),
                        rs.getObject("date_ordered", LocalDate.class)))
                .list();
    }

    /**
     * Deletes the {@code S_ORD} master row, returning the number of rows removed (0 if the id was
     * unknown). This is the bare master delete: the {@code S_ORD_S_ITEM} Non-Isolated guard that
     * vetoes it while line items exist lives one layer up, in {@link
     * com.example.summit.service.OrderService#delete} ({@code delete-cascade-guard}) — the repository
     * carries no policy, only SQL.
     */
    public int deleteOrder(long orderId) {
        return jdbcClient.sql("DELETE FROM s_ord WHERE id = :id")
                .param("id", orderId)
                .update();
    }
}
