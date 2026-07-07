package com.example.summit.data;

import com.example.summit.domain.Order;
import com.example.summit.domain.OrderSearchResult;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Data layer for the ORDERS master block ({@code S_ORD}). Spring {@link JdbcClient}
 * (PLAN: chosen over jOOQ to avoid Oracle-dialect licensing), same boundary as the
 * scaffold's {@code ServerInfoRepository}.
 *
 * <p>Both reads <strong>join</strong> {@code S_CUSTOMER} and {@code S_EMP} so the
 * customer name and sales-rep name arrive with the order row in a single round-trip.
 * This is where the Forms {@code S_ORD.POST-QUERY} select-into lookups dissolve
 * (ADR — no N+1): Forms fired a per-row {@code SELECT ... INTO :S_ORD.CUSTOMER_NAME}
 * after every fetched record; the web reads the names in the same query.
 * {@code S_EMP} is joined with a <em>left</em> join because {@code SALES_REP_ID} is
 * nullable — an order without a sales rep still loads.
 */
@Repository
public class OrderRepository {

    /**
     * The joined projection shared by {@link #loadGraph(long)} and {@link #search}. The
     * customer join is inner ({@code CUSTOMER_ID} is NOT NULL); the employee join is
     * left ({@code SALES_REP_ID} is nullable).
     */
    private static final String FROM_JOINED = """
            FROM s_ord o
            JOIN s_customer c ON c.id = o.customer_id
            LEFT JOIN s_emp e ON e.id = o.sales_rep_id
            """;

    private final JdbcClient jdbcClient;

    public OrderRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Loads a single order's header in one joined read — the customer and sales-rep
     * names come back on the same row (POST-QUERY dissolved). Returns empty when no
     * order has the given id.
     */
    public Optional<Order> loadGraph(long id) {
        return jdbcClient
                .sql("""
                        SELECT o.id, o.customer_id, c.name AS customer_name,
                               o.date_ordered, o.date_shipped,
                               o.sales_rep_id, e.first_name AS rep_first, e.last_name AS rep_last,
                               o.payment_type, o.order_filled
                        """ + FROM_JOINED + """
                        WHERE o.id = :id
                        """)
                .param("id", id)
                .query(OrderRepository::mapOrder)
                .optional();
    }

    /**
     * Quick search: returns orders whose id, customer name, or sales-rep name matches
     * the (case-insensitive, substring) term, as autocomplete suggestions ordered by
     * id. A blank term returns nothing (the presenter guards this too). Capped at 20
     * suggestions — enough for a type-ahead, and it keeps the overlay to one page.
     */
    public List<OrderSearchResult> search(String term) {
        String needle = term == null ? "" : term.trim();
        if (needle.isEmpty()) {
            return List.of();
        }
        return jdbcClient
                .sql("""
                        SELECT o.id, c.name AS customer_name,
                               e.first_name AS rep_first, e.last_name AS rep_last
                        """ + FROM_JOINED + """
                        WHERE TO_CHAR(o.id) LIKE '%' || :needle || '%'
                           OR LOWER(c.name) LIKE '%' || LOWER(:needle) || '%'
                           OR LOWER(TRIM(e.first_name || ' ' || e.last_name)) LIKE '%' || LOWER(:needle) || '%'
                        ORDER BY o.id
                        FETCH FIRST 20 ROWS ONLY
                        """)
                .param("needle", needle)
                .query((rs, rowNum) -> new OrderSearchResult(
                        rs.getLong("id"),
                        rs.getString("customer_name"),
                        repName(rs)))
                .list();
    }

    private static Order mapOrder(ResultSet rs, int rowNum) throws SQLException {
        // Read sales_rep_id and its null-ness together — wasNull() reflects the *last*
        // column read, so capturing it before reading any other column is required.
        long salesRepIdValue = rs.getLong("sales_rep_id");
        Long salesRepId = rs.wasNull() ? null : salesRepIdValue;
        return new Order(
                rs.getLong("id"),
                rs.getLong("customer_id"),
                rs.getString("customer_name"),
                toLocalDate(rs.getDate("date_ordered")),
                toLocalDate(rs.getDate("date_shipped")),
                salesRepId,
                repName(rs),
                rs.getString("payment_type"),
                "Y".equals(rs.getString("order_filled")));
    }

    /**
     * The sales-rep display name as {@code "<first> <last>"} — the name Forms
     * POST-QUERY put into {@code S_ORD.SALES_REP_NAME}. Null when the order has no rep
     * (the left join yields null name columns).
     */
    private static String repName(ResultSet rs) throws SQLException {
        String last = rs.getString("rep_last");
        if (last == null) {
            return null;
        }
        String first = rs.getString("rep_first");
        return (first == null || first.isBlank()) ? last : first + " " + last;
    }

    private static LocalDate toLocalDate(java.sql.Date date) {
        return date == null ? null : date.toLocalDate();
    }
}
