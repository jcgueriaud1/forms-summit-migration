package com.example.summit.data;

import com.example.summit.domain.Order;
import com.example.summit.domain.OrderSearchCriteria;
import com.example.summit.domain.OrderSummary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data layer for the {@code S_ORD} master — the persistence boundary, Spring
 * {@link JdbcClient}. All SQL lives here; the view and presenter hold none.
 *
 * <p>Table names are <strong>unqualified</strong> on purpose: the app connects as
 * the schema owner ({@code summit}) so unqualified names resolve to its own objects,
 * and the integration harness connects as its own test user with the same slice
 * tables — the same SQL runs against both.
 */
@Repository
public class OrderRepository {

    private final JdbcClient jdbcClient;

    public OrderRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Loads one order's header with its customer and sales-rep <em>display names</em>
     * resolved in a single round-trip — the join dissolves the two Forms POST-QUERY
     * lookups ({@code customer_name}, {@code sales_rep_name}) with no N+1.
     *
     * <p>{@code S_CUSTOMER} is an inner join ({@code CUSTOMER_ID} is mandatory);
     * {@code S_EMP} is a left join because {@code SALES_REP_ID} is nullable — an order
     * with no sales rep still loads, with a {@code null} sales-rep name.
     */
    public Optional<Order> loadGraph(long orderId) {
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
                        rs.getObject("date_ordered", java.time.LocalDate.class),
                        rs.getObject("date_shipped", java.time.LocalDate.class),
                        rs.getObject("sales_rep_id", Long.class),
                        rs.getString("sales_rep_name"),
                        rs.getString("payment_type"),
                        "Y".equals(rs.getString("order_filled"))))
                .optional();
    }

    /**
     * Returns the result set matching the quick-search criteria — the suggestions the
     * autocomplete offers. The {@link OrderSearchCriteria} decides what to match; this
     * method renders that decision as SQL. Customer / sales-rep names are joined so the
     * term can match them and so each suggestion can show them. Capped at 50 rows.
     */
    public List<OrderSummary> findByCriteria(OrderSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder("""
                SELECT o.id, c.name AS customer_name, e.last_name AS sales_rep_name, o.date_ordered
                FROM s_ord o
                JOIN s_customer c ON c.id = o.customer_id
                LEFT JOIN s_emp e ON e.id = o.sales_rep_id
                """);

        boolean matchOrderId = criteria.matchesOrderId() && criteria.orderId().isPresent();
        if (!criteria.isEmpty()) {
            List<String> predicates = new ArrayList<>(List.of(
                    "UPPER(c.name) LIKE :pattern",
                    "UPPER(e.last_name) LIKE :pattern"));
            if (matchOrderId) {
                predicates.add("o.id = :orderId");
            }
            sql.append("WHERE ").append(String.join(" OR ", predicates)).append('\n');
        }
        sql.append("ORDER BY o.id FETCH FIRST 50 ROWS ONLY");

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
                        rs.getObject("date_ordered", java.time.LocalDate.class)))
                .list();
    }
}
