package com.example.summit.service;

import com.example.summit.data.OrderRepository;
import com.example.summit.data.SummitDbIT;
import com.example.summit.domain.CommitResult;
import com.example.summit.domain.Order;
import com.example.summit.domain.OrderLine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the {@code COMMIT_FORM} whole-graph save + {@code ORA_ROWSCN} optimistic locking
 * ({@code transaction-commit-form} / {@code concurrency-pessimistic-to-scn}, issue #19) against a
 * real Summit Oracle. The headlines:
 * <ul>
 *   <li>one {@link OrderService#save(Order)} persists the header <em>and</em> its line items —
 *       inserts, updates and deletes — as one whole-graph operation;</li>
 *   <li>a new order draws its id from {@code S_ORD_ID.nextval} (the {@code PRE-INSERT});</li>
 *   <li>the DB {@code AUDIT_S_ITEM} trigger fires on every line insert, inside the commit, with zero
 *       Java;</li>
 *   <li>a concurrent change since load (a moved {@code ORA_ROWSCN}) makes a stale save fail with a
 *       typed {@link CommitResult.Conflict} rather than silently overwriting;</li>
 *   <li>the commit gate refuses still-invalid input ({@link CommitResult.Rejected}), persisting
 *       nothing.</li>
 * </ul>
 *
 * <p>These write to the shared Summit container, so every fixture is a throwaway order created here
 * (id from the sequence, well above the seeded 50001-50003) — the seed data sibling IT classes rely
 * on stays intact. As with the delete guard IT, the {@code @Transactional} boundary is a no-op on
 * this hand-wired service; the conflict and gate checks run <em>before</em> any write, so a refused
 * save leaves nothing behind regardless.
 */
class OrderCommitIT extends SummitDbIT {

    private final OrderRepository repository = new OrderRepository(jdbcClient());
    private final OrderService service = new OrderService(repository);

    /** Every throwaway order this class creates — removed after each test so the shared seed is pristine. */
    private final List<Long> createdOrderIds = new ArrayList<>();

    @AfterEach
    void removeCreatedOrders() {
        for (long orderId : createdOrderIds) {
            jdbcClient().sql("DELETE FROM s_item WHERE ord_id = :id").param("id", orderId).update();
            jdbcClient().sql("DELETE FROM s_ord WHERE id = :id").param("id", orderId).update();
        }
        createdOrderIds.clear();
    }

    // Two lines every fixture order starts with: Ski Boots (41010) and Ski Pole (20108).
    private static final OrderLine BOOTS = new OrderLine(1, 41010, "Ski Boots", new BigDecimal("50.00"), 5, 5);
    private static final OrderLine POLE = new OrderLine(2, 20108, "Ski Pole", new BigDecimal("13.00"), 27, 27);

    @Test
    void newOrderDrawsItsIdFromTheSequenceAndInsertsItsLines() {
        long auditBefore = auditRowCount();

        Order toSave = newOrder(List.of(BOOTS, POLE));
        CommitResult result = service.save(toSave);

        // New order → id from S_ORD_ID.nextval (sequence starts at 70000, above the seed).
        CommitResult.Committed committed = assertInstanceOf(CommitResult.Committed.class, result);
        createdOrderIds.add(committed.orderId());
        assertTrue(committed.orderId() >= 70000, "new id should come from S_ORD_ID.nextval");

        Order persisted = repository.loadGraph(committed.orderId()).orElseThrow();
        assertEquals(201L, persisted.customerId());
        assertEquals(List.of(1L, 2L), itemIds(persisted));
        // TOTAL persisted from the domain Sum: 5*50 + 27*13 = 601.
        assertEquals(0, new BigDecimal("601.00").compareTo(persisted.total()));

        // The DB AUDIT_S_ITEM trigger fired once per inserted line, inside the same commit.
        assertEquals(auditBefore + 2, auditRowCount());
    }

    @Test
    void oneCommitInsertsUpdatesAndDeletesLinesTogether() {
        Order loaded = createFixtureOrder();

        // Build the working graph: re-price line 1, drop line 2, add a brand-new line 3.
        List<OrderLine> working = new ArrayList<>();
        working.add(new OrderLine(1, 41010, "Ski Boots", new BigDecimal("60.00"), 5, 5));   // update
        working.add(new OrderLine(3, 30421, "Pro Ski Boot", new BigDecimal("1000.00"), 2, 2)); // insert
        // (line 2 omitted -> delete)

        CommitResult result = service.save(withLines(loaded, working));

        assertInstanceOf(CommitResult.Committed.class, result);
        Order persisted = repository.loadGraph(loaded.id()).orElseThrow();
        // The insert, the update and the delete all landed from the single save.
        assertEquals(List.of(1L, 3L), itemIds(persisted));
        assertEquals(0, new BigDecimal("60.00").compareTo(persisted.lines().get(0).price())); // updated
        assertEquals(30421L, persisted.lines().get(1).productId());                            // inserted
        // TOTAL reconciled to the new graph: 5*60 + 2*1000 = 2300.
        assertEquals(0, new BigDecimal("2300.00").compareTo(persisted.total()));
    }

    @Test
    void aStaleSaveFailsWithConflictAndDoesNotOverwrite() {
        Order loaded = createFixtureOrder(); // order_filled = true, payment CASH, scn0

        // Session B commits a change first (order_filled -> false), advancing the row's ORA_ROWSCN.
        assertInstanceOf(CommitResult.Committed.class,
                service.save(withOrderFilled(loaded, false)));

        // Session A still holds the pre-change scn (loaded) and tries to flip payment to CREDIT.
        CommitResult result = service.save(withPaymentType(loaded, "CREDIT"));

        // The moved scn is detected: a typed Conflict, and nothing of session A's is written.
        assertInstanceOf(CommitResult.Conflict.class, result);
        Order after = repository.loadGraph(loaded.id()).orElseThrow();
        assertFalse(after.orderFilled());                 // session B's change survived
        assertEquals("CASH", after.paymentType());        // session A's stale CREDIT did NOT overwrite
    }

    @Test
    void theCommitGateRefusesAnInvalidOrder() {
        Order loaded = createFixtureOrder(); // ordered 1992-08-31

        // A ship date before the order date violates the WHEN-VALIDATE-RECORD invariant.
        Order invalid = new Order(
                loaded.id(), loaded.customerId(), loaded.customerName(),
                LocalDate.of(1992, 8, 31), LocalDate.of(1992, 8, 1),
                loaded.salesRepId(), loaded.salesRepName(), loaded.paymentType(), loaded.orderFilled(),
                loaded.lines(), loaded.rowScn());

        CommitResult result = service.save(invalid);

        assertInstanceOf(CommitResult.Rejected.class, result);
        // The gate ran before any write: the persisted ship date is untouched.
        Order after = repository.loadGraph(loaded.id()).orElseThrow();
        assertEquals(LocalDate.of(1992, 9, 10), after.dateShipped());
    }

    // ---- fixtures ------------------------------------------------------------------------------

    /** A new (unpersisted) order with the given lines — customer 201 / rep 1, CASH, filled. */
    private static Order newOrder(List<OrderLine> lines) {
        return new Order(0, 201, "Unisports", LocalDate.of(1992, 8, 31), LocalDate.of(1992, 9, 10),
                1L, "Magee", "CASH", true, lines, null);
    }

    /** Persists a fresh two-line throwaway order and returns it reloaded (with its ORA_ROWSCN). */
    private Order createFixtureOrder() {
        CommitResult.Committed committed =
                (CommitResult.Committed) service.save(newOrder(List.of(BOOTS, POLE)));
        createdOrderIds.add(committed.orderId());
        return repository.loadGraph(committed.orderId()).orElseThrow();
    }

    private static Order withLines(Order order, List<OrderLine> lines) {
        return new Order(order.id(), order.customerId(), order.customerName(),
                order.dateOrdered(), order.dateShipped(), order.salesRepId(), order.salesRepName(),
                order.paymentType(), order.orderFilled(), lines, order.rowScn());
    }

    private static Order withOrderFilled(Order order, boolean orderFilled) {
        return new Order(order.id(), order.customerId(), order.customerName(),
                order.dateOrdered(), order.dateShipped(), order.salesRepId(), order.salesRepName(),
                order.paymentType(), orderFilled, order.lines(), order.rowScn());
    }

    private static Order withPaymentType(Order order, String paymentType) {
        return new Order(order.id(), order.customerId(), order.customerName(),
                order.dateOrdered(), order.dateShipped(), order.salesRepId(), order.salesRepName(),
                paymentType, order.orderFilled(), order.lines(), order.rowScn());
    }

    private static List<Long> itemIds(Order order) {
        return order.lines().stream().map(OrderLine::itemId).toList();
    }

    private long auditRowCount() {
        return jdbcClient().sql("SELECT COUNT(*) FROM s_item_audit").query(Long.class).single();
    }
}
