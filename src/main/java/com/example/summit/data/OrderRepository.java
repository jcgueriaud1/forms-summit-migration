package com.example.summit.data;

import com.example.summit.domain.Order;
import com.example.summit.domain.OrderSummary;
import com.example.summit.domain.PaymentType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Data layer for the {@code S_ORD} master — the persistence boundary
 * ({@link JdbcClient}, as chosen for the scaffold).
 *
 * <p>Both reads <strong>join</strong> {@code S_CUSTOMER} and {@code S_EMP} so the
 * customer name and sales-rep name arrive with the row. That dissolves the Forms
 * {@code S_ORD.POST-QUERY} lookup (which re-fetched those display fields per record)
 * into a single query — no N+1, no per-row round-trip. The sales-rep name follows the
 * POST-QUERY join exactly: {@code E.last_name} where {@code E.id = C.sales_rep_id} (the
 * customer's rep), left-joined so an order for a customer without a rep still loads.
 */
@Repository
public class OrderRepository {

    private final JdbcClient jdbcClient;

    public OrderRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Quick search: returns orders whose id, customer name, or sales-rep name matches
     * the term (case-insensitive substring). A blank/absent term matches all — the
     * empty state simply lists orders. This is the web form of Forms'
     * {@code KEY-EXEQRY}: the search commits a result set the clerk picks one row from.
     */
    public List<OrderSummary> search(String term) {
        String normalized = (term == null || term.isBlank()) ? null : term.trim();
        return jdbcClient
                .sql("""
                        SELECT o.id, c.name AS customer_name, e.last_name AS sales_rep_name
                        FROM s_ord o
                        JOIN s_customer c ON c.id = o.customer_id
                        LEFT JOIN s_emp e ON e.id = c.sales_rep_id
                        WHERE :term IS NULL
                           OR TO_CHAR(o.id) LIKE '%' || :term || '%'
                           OR UPPER(c.name) LIKE '%' || UPPER(:term) || '%'
                           OR UPPER(e.last_name) LIKE '%' || UPPER(:term) || '%'
                        ORDER BY o.id
                        FETCH FIRST 50 ROWS ONLY
                        """)
                .param("term", normalized)
                .query((rs, rowNum) -> new OrderSummary(
                        rs.getInt("id"),
                        rs.getString("customer_name"),
                        rs.getString("sales_rep_name")))
                .list();
    }

    /**
     * Loads one order's header with its display names already joined — the single read
     * behind selecting a search suggestion. Returns empty when no such order exists.
     */
    public Optional<Order> loadGraph(int orderId) {
        return jdbcClient
                .sql("""
                        SELECT o.id, o.customer_id, o.date_ordered, o.date_shipped, o.sales_rep_id,
                               o.payment_type, o.order_filled,
                               c.name AS customer_name, e.last_name AS sales_rep_name
                        FROM s_ord o
                        JOIN s_customer c ON c.id = o.customer_id
                        LEFT JOIN s_emp e ON e.id = c.sales_rep_id
                        WHERE o.id = :id
                        """)
                .param("id", orderId)
                .query((rs, rowNum) -> mapOrder(rs))
                .optional();
    }

    private static Order mapOrder(ResultSet rs) throws SQLException {
        String paymentType = rs.getString("payment_type");
        return new Order(
                rs.getInt("id"),
                rs.getInt("customer_id"),
                rs.getString("customer_name"),
                rs.getObject("date_ordered", LocalDate.class),
                rs.getObject("date_shipped", LocalDate.class),
                rs.getObject("sales_rep_id", Integer.class),
                rs.getString("sales_rep_name"),
                paymentType == null ? null : PaymentType.valueOf(paymentType),
                "Y".equals(rs.getString("order_filled")));
    }
}
