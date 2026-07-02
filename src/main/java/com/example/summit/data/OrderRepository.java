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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
     *
     * <p>The row's {@code ORA_ROWSCN} is read alongside as the order's optimistic-lock token
     * (issue #48): the whole-graph commit re-checks it in a guarded {@code UPDATE} to detect a
     * concurrent change since load, the web replacement for Forms' pessimistic row lock.
     */
    private Optional<Order> loadHeader(long orderId) {
        return jdbcClient
                .sql("""
                        SELECT o.id, o.customer_id, c.name AS customer_name,
                               o.date_ordered, o.date_shipped,
                               o.sales_rep_id, e.last_name AS sales_rep_name,
                               o.payment_type, o.order_filled, o.ORA_ROWSCN AS scn
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
                        rs.getLong("scn"),
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
                header.paymentType(), header.orderFilled(), header.scn(),
                lines);
    }

    /**
     * Returns the orders matching the search criteria — the result set the search commits and the
     * autocomplete draws its suggestions from. {@link OrderSearchCriteria} decides what to match; this
     * method renders that decision as SQL (issue #43). The quick term is an OR group over order id /
     * customer / sales rep; each set advanced filter (customer, sales rep, payment type, ordered-date
     * range, status) is an additional constraint, and the quick group and every advanced filter combine
     * with <strong>AND</strong>. Customer and sales-rep names are joined so the term can match them and
     * each suggestion can display them, capped at {@value #MAX_SUGGESTIONS} rows.
     */
    public List<OrderSummary> findByCriteria(OrderSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder("""
                SELECT o.id, c.name AS customer_name, e.last_name AS sales_rep_name, o.date_ordered
                FROM s_ord o
                JOIN s_customer c ON c.id = o.customer_id
                LEFT JOIN s_emp e ON e.id = o.sales_rep_id
                """);

        // Each entry pairs its AND-combined SQL predicate with the params it binds, so the WHERE clause
        // and its parameters are assembled together and stay in lockstep.
        List<String> predicates = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();

        if (criteria.hasQuickTerm()) {
            // The quick group is itself an OR over id / customer / sales rep, parenthesised so it ANDs
            // as one unit with the advanced filters.
            List<String> quick = new ArrayList<>(List.of(
                    "UPPER(c.name) LIKE :quick",
                    "UPPER(e.last_name) LIKE :quick"));
            params.put("quick", criteria.namePattern());
            criteria.orderId().ifPresent(id -> {
                quick.add("o.id = :quickId");
                params.put("quickId", id);
            });
            predicates.add("(" + String.join(" OR ", quick) + ")");
        }
        criteria.customerPattern().ifPresent(pattern -> {
            predicates.add("UPPER(c.name) LIKE :customer");
            params.put("customer", pattern);
        });
        criteria.salesRepPattern().ifPresent(pattern -> {
            predicates.add("UPPER(e.last_name) LIKE :salesRep");
            params.put("salesRep", pattern);
        });
        if (criteria.hasPaymentType()) {
            predicates.add("o.payment_type = :paymentType");
            params.put("paymentType", criteria.paymentType());
        }
        if (criteria.orderedFrom() != null) {
            predicates.add("o.date_ordered >= :orderedFrom");
            params.put("orderedFrom", criteria.orderedFrom());
        }
        if (criteria.orderedTo() != null) {
            predicates.add("o.date_ordered <= :orderedTo");
            params.put("orderedTo", criteria.orderedTo());
        }
        if (criteria.status() != null) {
            predicates.add("o.order_filled = :status");
            params.put("status", criteria.status().flag());
        }

        if (!predicates.isEmpty()) {
            sql.append("WHERE ").append(String.join(" AND ", predicates)).append('\n');
        }
        sql.append("ORDER BY o.id FETCH FIRST ").append(MAX_SUGGESTIONS).append(" ROWS ONLY");

        JdbcClient.StatementSpec spec = jdbcClient.sql(sql.toString());
        for (Map.Entry<String, Object> param : params.entrySet()) {
            spec = spec.param(param.getKey(), param.getValue());
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

    // ---- Whole-graph commit SQL (issue #48) --------------------------------------------------
    //
    // The atomic write primitives the whole-graph COMMIT_FORM save is composed of; the
    // orchestration (which lines to insert/update/delete, the ORA_ROWSCN gate decision) lives one
    // layer up in OrderService.save — the repository carries no policy, only SQL.

    /**
     * The next {@code S_ORD} id from {@code S_ORD_ID.nextval} — the header {@code PRE-INSERT}
     * ({@code orders-form-spec.md}). Drawn by the commit for a new order before its header insert,
     * so a web-created order gets its id from the same sequence Forms used.
     */
    public long nextOrderId() {
        return jdbcClient.sql("SELECT s_ord_id.nextval FROM dual")
                .query(Long.class)
                .single();
    }

    /**
     * Inserts a new {@code S_ORD} header (its id already drawn from {@link #nextOrderId()}). The
     * {@code total} column is written from the order's computed {@link Order#total()} so the stored
     * summary stays consistent with the lines, and {@code order_filled} is encoded back to its
     * {@code Y}/{@code N} CHECK domain.
     */
    public void insertHeader(Order order) {
        jdbcClient.sql("""
                        INSERT INTO s_ord (id, customer_id, date_ordered, date_shipped,
                                           sales_rep_id, total, payment_type, order_filled)
                        VALUES (:id, :customerId, :dateOrdered, :dateShipped,
                                :salesRepId, :total, :paymentType, :orderFilled)
                        """)
                .param("id", order.id())
                .param("customerId", order.customerId())
                .param("dateOrdered", order.dateOrdered())
                .param("dateShipped", order.dateShipped())
                .param("salesRepId", order.salesRepId())
                .param("total", order.total())
                .param("paymentType", order.paymentType())
                .param("orderFilled", order.orderFilled() ? "Y" : "N")
                .update();
    }

    /**
     * Updates an existing {@code S_ORD} header <em>only if its {@code ORA_ROWSCN} is unchanged
     * since load</em> — the optimistic-concurrency gate ({@code concurrency-pessimistic-to-scn}).
     * Returns the number of rows updated: {@code 1} when the scn still matches (the header is
     * written and the row's scn advances), {@code 0} when a concurrent commit has moved the scn —
     * the conflict signal the whole-graph save turns into a typed result rather than overwriting.
     */
    public int updateHeaderIfUnchanged(Order order) {
        return jdbcClient.sql("""
                        UPDATE s_ord
                           SET customer_id = :customerId, date_ordered = :dateOrdered,
                               date_shipped = :dateShipped, sales_rep_id = :salesRepId,
                               total = :total, payment_type = :paymentType, order_filled = :orderFilled
                         WHERE id = :id AND ORA_ROWSCN = :scn
                        """)
                .param("id", order.id())
                .param("customerId", order.customerId())
                .param("dateOrdered", order.dateOrdered())
                .param("dateShipped", order.dateShipped())
                .param("salesRepId", order.salesRepId())
                .param("total", order.total())
                .param("paymentType", order.paymentType())
                .param("orderFilled", order.orderFilled() ? "Y" : "N")
                .param("scn", order.scn())
                .update();
    }

    /** The {@code item_id}s of an order's persisted {@code S_ITEM} lines — the baseline the commit
     * diffs the working lines against to decide inserts, updates, and deletes. */
    public List<Long> existingItemIds(long orderId) {
        return jdbcClient.sql("SELECT item_id FROM s_item WHERE ord_id = :id")
                .param("id", orderId)
                .query(Long.class)
                .list();
    }

    /** Inserts one {@code S_ITEM} line under the given order (its {@code item_id} already assigned
     * per-order {@code MAX+1}, issue #45). Fires the DB {@code AUDIT_S_ITEM} trigger in-transaction. */
    public void insertLine(long orderId, OrderLine line) {
        jdbcClient.sql("""
                        INSERT INTO s_item (ord_id, item_id, product_id, price, quantity, quantity_shipped)
                        VALUES (:ordId, :itemId, :productId, :price, :quantity, :quantityShipped)
                        """)
                .param("ordId", orderId)
                .param("itemId", line.itemId())
                .param("productId", line.productId())
                .param("price", line.price())
                .param("quantity", line.quantity())
                .param("quantityShipped", line.quantityShipped())
                .update();
    }

    /** Updates one existing {@code S_ITEM} line in place, keyed by its {@code (ord_id, item_id)} PK. */
    public void updateLine(long orderId, OrderLine line) {
        jdbcClient.sql("""
                        UPDATE s_item
                           SET product_id = :productId, price = :price,
                               quantity = :quantity, quantity_shipped = :quantityShipped
                         WHERE ord_id = :ordId AND item_id = :itemId
                        """)
                .param("ordId", orderId)
                .param("itemId", line.itemId())
                .param("productId", line.productId())
                .param("price", line.price())
                .param("quantity", line.quantity())
                .param("quantityShipped", line.quantityShipped())
                .update();
    }

    /** Deletes one {@code S_ITEM} line — a detail row the clerk removed from the working set. */
    public void deleteLine(long orderId, long itemId) {
        jdbcClient.sql("DELETE FROM s_item WHERE ord_id = :ordId AND item_id = :itemId")
                .param("ordId", orderId)
                .param("itemId", itemId)
                .update();
    }
}
