package com.example.summit.data;

import com.example.summit.domain.Order;
import com.example.summit.domain.OrderSummary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Data layer for the {@code S_ORD} master — the persistence boundary, Spring
 * {@link JdbcClient} (PLAN: chosen over jOOQ to avoid Oracle-dialect licensing).
 *
 * <p>Two reads back the ORDERS tracer bullet (issue #85):
 * <ul>
 *   <li>{@link #search(String)} — the quick-search suggestion query (Forms
 *       {@code KEY-EXEQRY} / {@code EXECUTE_QUERY}), matching a term against order id,
 *       customer name, or sales-rep last name.</li>
 *   <li>{@link #loadGraph(long)} — the single read that loads one order's header. Its SQL
 *       <strong>joins</strong> {@code S_CUSTOMER} and {@code S_EMP} so {@code
 *       customer_name} and {@code sales_rep_name} arrive with the row, dissolving the
 *       Forms {@code POST-QUERY} lookups into one round-trip (no N+1). Named {@code
 *       loadGraph} because later slices extend it to pull the {@code S_ITEM} line graph in
 *       the same load.</li>
 * </ul>
 *
 * <p>Both use a {@code LEFT JOIN} to {@code S_EMP}: {@code S_ORD.sales_rep_id} is nullable
 * (e.g. Summit order 50003), and an inner join would silently drop rep-less orders.
 */
@Repository
public class OrderRepository {

    private final JdbcClient jdbcClient;

    public OrderRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Loads one order's header in a single read, joining the customer and sales-rep names
     * so they arrive with the row (the dissolved {@code POST-QUERY} lookups).
     */
    public Optional<Order> loadGraph(long id) {
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
                .param("id", id)
                .query((rs, rowNum) -> {
                    // Oracle returns NUMBER as BigDecimal; getLong + wasNull is the
                    // null-safe idiom for the nullable sales_rep_id FK.
                    long salesRepId = rs.getLong("sales_rep_id");
                    return new Order(
                            rs.getLong("id"),
                            rs.getLong("customer_id"),
                            rs.getString("customer_name"),
                            rs.getObject("date_ordered", java.time.LocalDate.class),
                            rs.getObject("date_shipped", java.time.LocalDate.class),
                            rs.wasNull() ? null : salesRepId,
                            rs.getString("sales_rep_name"),
                            rs.getString("payment_type"),
                            "Y".equals(rs.getString("order_filled")));
                })
                .optional();
    }

    /**
     * Returns orders whose id, customer name, or sales-rep last name contains {@code term}
     * (case-insensitive), as quick-search suggestions. An empty term matches everything.
     * Capped so a broad term cannot flood the autocomplete overlay.
     */
    public List<OrderSummary> search(String term) {
        String needle = term == null ? "" : term.trim();
        return jdbcClient
                .sql("""
                        SELECT o.id, c.name AS customer_name, e.last_name AS sales_rep_name
                        FROM s_ord o
                        JOIN s_customer c ON c.id = o.customer_id
                        LEFT JOIN s_emp e ON e.id = o.sales_rep_id
                        WHERE :term IS NULL
                           OR TO_CHAR(o.id) LIKE '%' || :term || '%'
                           OR UPPER(c.name) LIKE '%' || UPPER(:term) || '%'
                           OR UPPER(e.last_name) LIKE '%' || UPPER(:term) || '%'
                        ORDER BY o.id
                        FETCH FIRST 50 ROWS ONLY
                        """)
                .param("term", needle.isEmpty() ? null : needle)
                .query((rs, rowNum) -> new OrderSummary(
                        rs.getLong("id"),
                        rs.getString("customer_name"),
                        rs.getString("sales_rep_name")))
                .list();
    }
}
