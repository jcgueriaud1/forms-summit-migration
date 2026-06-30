package com.example.summit.data;

import com.example.summit.domain.Order;
import com.example.summit.domain.OrderLine;
import com.example.summit.domain.OrderSearchCriteria;
import com.example.summit.domain.OrderSummary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
     * Loads the whole order graph — header <em>and</em> its {@code S_ITEM} line items — on a
     * single query path. The joins dissolve every Forms POST-QUERY lookup with no N+1:
     * {@code customer_name} / {@code sales_rep_name} on the header, and each line's product
     * {@code description} ({@code S_PRODUCT.name}) arrives on its own row.
     *
     * <p>{@code S_CUSTOMER} is an inner join ({@code CUSTOMER_ID} is mandatory); the others are
     * left joins: {@code S_EMP} because {@code SALES_REP_ID} is nullable (an order with no sales
     * rep still loads), and {@code S_ITEM}/{@code S_PRODUCT} so an order with no line items still
     * returns its header (with an empty {@link Order#lines()} list). One header row is repeated
     * per line; a {@code ResultSetExtractor} folds the rows back into one aggregate, ordered by
     * {@code item_id} so the detail grid mirrors the Forms block order.
     */
    public Optional<Order> loadGraph(long orderId) {
        return jdbcClient
                .sql("""
                        SELECT o.id, o.customer_id, c.name AS customer_name,
                               o.date_ordered, o.date_shipped,
                               o.sales_rep_id, e.last_name AS sales_rep_name,
                               o.payment_type, o.order_filled,
                               i.item_id, i.product_id, p.name AS product_name,
                               i.price, i.quantity, i.quantity_shipped
                        FROM s_ord o
                        JOIN s_customer c ON c.id = o.customer_id
                        LEFT JOIN s_emp e ON e.id = o.sales_rep_id
                        LEFT JOIN s_item i ON i.ord_id = o.id
                        LEFT JOIN s_product p ON p.id = i.product_id
                        WHERE o.id = :id
                        ORDER BY i.item_id
                        """)
                .param("id", orderId)
                .query(rs -> {
                    if (!rs.next()) {
                        return Optional.<Order>empty();
                    }
                    // Header facts are identical on every row — read them once from the first.
                    long id = rs.getLong("id");
                    long customerId = rs.getLong("customer_id");
                    String customerName = rs.getString("customer_name");
                    LocalDate dateOrdered = rs.getObject("date_ordered", LocalDate.class);
                    LocalDate dateShipped = rs.getObject("date_shipped", LocalDate.class);
                    Long salesRepId = rs.getObject("sales_rep_id", Long.class);
                    String salesRepName = rs.getString("sales_rep_name");
                    String paymentType = rs.getString("payment_type");
                    boolean orderFilled = "Y".equals(rs.getString("order_filled"));

                    List<OrderLine> lines = new ArrayList<>();
                    do {
                        // A header with no lines still produces one row (left join), whose
                        // item_id is null — skip it so the line list stays empty.
                        Long itemId = rs.getObject("item_id", Long.class);
                        if (itemId != null) {
                            lines.add(new OrderLine(
                                    itemId,
                                    rs.getLong("product_id"),
                                    rs.getString("product_name"),
                                    rs.getBigDecimal("price"),
                                    rs.getLong("quantity"),
                                    rs.getLong("quantity_shipped")));
                        }
                    } while (rs.next());

                    return Optional.of(new Order(
                            id, customerId, customerName, dateOrdered, dateShipped,
                            salesRepId, salesRepName, paymentType, orderFilled, List.copyOf(lines)));
                });
    }

    /**
     * Returns the result set matching the search criteria — the autocomplete suggestions and,
     * once a search is run, the set the prev/next pager steps. The {@link OrderSearchCriteria}
     * decides what to match; this method renders that decision as SQL. Customer / sales-rep
     * names are joined so the term can match them and so each suggestion can show them.
     *
     * <p>The clauses compose as QBE does: the quick term is one OR-group (id <em>or</em>
     * customer name <em>or</em> rep name) and every advanced filter that is set is one further
     * AND-ed clause, so advanced criteria narrow the quick term rather than widen it. An empty
     * criteria adds no {@code WHERE} and returns every order. Capped at 50 rows.
     */
    public List<OrderSummary> findByCriteria(OrderSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder("""
                SELECT o.id, c.name AS customer_name, e.last_name AS sales_rep_name, o.date_ordered
                FROM s_ord o
                JOIN s_customer c ON c.id = o.customer_id
                LEFT JOIN s_emp e ON e.id = o.sales_rep_id
                """);

        // Each set criterion becomes one AND-ed clause; the quick term is itself an OR-group.
        boolean matchOrderId = criteria.matchesOrderId() && criteria.orderId().isPresent();
        List<String> clauses = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();

        if (criteria.hasQuickTerm()) {
            List<String> quickGroup = new ArrayList<>(List.of(
                    "UPPER(c.name) LIKE :quickPattern",
                    "UPPER(e.last_name) LIKE :quickPattern"));
            params.put("quickPattern", criteria.namePattern());
            if (matchOrderId) {
                quickGroup.add("o.id = :quickOrderId");
                params.put("quickOrderId", criteria.orderId().getAsLong());
            }
            clauses.add("(" + String.join(" OR ", quickGroup) + ")");
        }
        if (criteria.hasCustomer()) {
            clauses.add("UPPER(c.name) LIKE :customerPattern");
            params.put("customerPattern", criteria.customerPattern());
        }
        if (criteria.hasSalesRep()) {
            clauses.add("UPPER(e.last_name) LIKE :salesRepPattern");
            params.put("salesRepPattern", criteria.salesRepPattern());
        }
        if (criteria.hasPaymentType()) {
            clauses.add("o.payment_type = :paymentType");
            params.put("paymentType", criteria.paymentType());
        }
        // Inclusive both ends — mirrors OrderSearchCriteria.matchesOrderedDate.
        if (criteria.hasOrderedFrom()) {
            clauses.add("o.date_ordered >= :orderedFrom");
            params.put("orderedFrom", criteria.orderedFrom());
        }
        if (criteria.hasOrderedTo()) {
            clauses.add("o.date_ordered <= :orderedTo");
            params.put("orderedTo", criteria.orderedTo());
        }
        if (criteria.hasOrderFilled()) {
            clauses.add("o.order_filled = :orderFilled");
            params.put("orderFilled", criteria.orderFilled() ? "Y" : "N");
        }

        if (!clauses.isEmpty()) {
            sql.append("WHERE ").append(String.join(" AND ", clauses)).append('\n');
        }
        sql.append("ORDER BY o.id FETCH FIRST 50 ROWS ONLY");

        return jdbcClient.sql(sql.toString())
                .params(params)
                .query((rs, rowNum) -> new OrderSummary(
                        rs.getLong("id"),
                        rs.getString("customer_name"),
                        rs.getString("sales_rep_name"),
                        rs.getObject("date_ordered", java.time.LocalDate.class)))
                .list();
    }
}
