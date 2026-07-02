package com.example.summit.service;

import com.example.summit.data.OrderRepository;
import com.example.summit.data.SummitDbIT;
import com.example.summit.domain.CascadeDeleteBlockedException;
import com.example.summit.domain.Order;
import com.example.summit.domain.OrderLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the {@code S_ORD_S_ITEM} <strong>Non-Isolated</strong> cascade-delete guard
 * ({@code delete-cascade-guard}, issue #45) against a real Summit Oracle. The headline: deleting an
 * order that still has line items is vetoed with a {@link CascadeDeleteBlockedException} and the
 * order survives — {@code S_ITEM} detail rows can never be orphaned, the web counterpart of Forms'
 * {@code ON-CHECK-DELETE-MASTER}. An order with no lines deletes cleanly, so the guard blocks the
 * dangerous case without forbidding the safe one.
 *
 * <p>The service is hand-wired (no Spring proxy), so its {@code @Transactional} is a no-op here —
 * which is exactly right for the guard: it throws <em>before</em> any {@code DELETE} runs, so no
 * partial state is left behind to roll back.
 */
class OrderServiceIT extends SummitDbIT {

    private final OrderRepository repository = new OrderRepository(jdbcClient());
    private final OrderService service = new OrderService(repository);

    @Test
    void deleteIsBlockedWhileLineItemsExist() {
        // 50001 has two S_ITEM lines — the Non-Isolated relation forbids deleting its master.
        CascadeDeleteBlockedException blocked =
                assertThrows(CascadeDeleteBlockedException.class, () -> service.delete(50001));
        assertTrue(blocked.getMessage().contains("50001"));

        // The guard fired before any DELETE, so the order (and its lines) are untouched.
        assertTrue(repository.loadGraph(50001).isPresent());
        assertEquals(2, repository.loadGraph(50001).orElseThrow().lines().size());
    }

    @Test
    void deleteSucceedsForAnOrderWithNoLineItems() {
        // A throwaway, line-free order (customer 201 exists) — created here so the shared seed data
        // stays intact for sibling IT classes. With no details, the Non-Isolated guard lets it go.
        jdbcClient().sql("""
                        INSERT INTO s_ord (id, customer_id, date_ordered, payment_type, order_filled)
                        VALUES (59999, 201, DATE '1992-09-09', 'CASH', 'N')
                        """)
                .update();

        service.delete(59999);

        assertTrue(repository.loadGraph(59999).isEmpty());
    }

    @Test
    void deletingAnUnknownOrderIsANoOp() {
        // No order, nothing to orphan — the guard neither throws nor deletes anything.
        service.delete(888888);
        assertFalse(repository.loadGraph(888888).isPresent());
    }

    // ---- Whole-graph COMMIT_FORM save + ORA_ROWSCN concurrency (issue #48) ----------------------
    //
    // These prove the commit climax against a real Summit Oracle: one save persists the header and
    // all its lines (insert/update/delete) together, a new order draws its id from S_ORD_ID.nextval,
    // an ORA_ROWSCN conflict fails the stale save rather than overwriting, and the commit gate
    // refuses still-invalid input. Each test creates its own throwaway order and removes it in a
    // finally, so the shared seed (asserted verbatim by sibling IT classes) stays intact.

    @Test
    void saveNewOrderDrawsIdFromSequenceAndInsertsTheWholeGraph() {
        Order toSave = newOrder(
                new OrderLine(1, 41010, "Ski Boots", new BigDecimal("50.00"), 5, 5),   // 5 * 50 = 250
                new OrderLine(2, 20108, "Ski Pole", new BigDecimal("13.00"), 10, 10));  // 10 * 13 = 130
        long newId = 0;
        try {
            SaveResult result = service.save(toSave);

            Order saved = assertInstanceOf(SaveResult.Committed.class, result).order();
            newId = saved.id();
            // The id came from S_ORD_ID.nextval (seeded to start at 100000), not from the input (0).
            assertTrue(newId >= 100000, "new order id should come from S_ORD_ID.nextval, was " + newId);

            // The whole graph is persisted: header + both lines, with the stored total consistent.
            Order reloaded = repository.loadGraph(newId).orElseThrow();
            assertEquals(201L, reloaded.customerId());
            assertEquals(List.of(1L, 2L), reloaded.lines().stream().map(OrderLine::itemId).toList());
            assertEquals(0, new BigDecimal("380.00").compareTo(reloaded.total()));
        } finally {
            deleteOrderGraph(newId);
        }
    }

    @Test
    void duplicatingAnOrderInsertsACopyWithAFreshIdAndLeavesTheOriginal() {
        // The master Duplicate order action (issue #49): seed an order, clone it through the Domain
        // KEY-DUPREC clear-list, and commit — the copy inserts under a fresh S_ORD_ID.nextval id
        // carrying the lines, and the original survives untouched (no overwrite).
        Order seeded = assertInstanceOf(SaveResult.Committed.class, service.save(newOrder(
                new OrderLine(1, 41010, "Ski Boots", new BigDecimal("50.00"), 5, 5),   // 250
                new OrderLine(2, 20108, "Ski Pole", new BigDecimal("13.00"), 10, 10)))).order(); // 130
        long originalId = seeded.id();
        long copyId = 0;
        try {
            Order copy = seeded.duplicateForInsert();
            assertTrue(copy.isNew(), "the duplicate must be a new (unsaved) order so it inserts");

            Order saved = assertInstanceOf(SaveResult.Committed.class, service.save(copy)).order();
            copyId = saved.id();

            // A fresh id from the sequence, distinct from the original — an insert, not an overwrite.
            assertTrue(copyId >= 100000, "duplicated order id should come from S_ORD_ID.nextval, was " + copyId);
            assertNotEquals(originalId, copyId);
            // The copy carries the duplicated lines and total...
            assertEquals(List.of(1L, 2L), saved.lines().stream().map(OrderLine::itemId).toList());
            assertEquals(0, new BigDecimal("380.00").compareTo(saved.total()));
            // ...and the original order is still there, unchanged.
            assertEquals(2, repository.loadGraph(originalId).orElseThrow().lines().size());
        } finally {
            deleteOrderGraph(copyId);
            deleteOrderGraph(originalId);
        }
    }

    @Test
    void saveReconcilesLinesInsertUpdateAndDeleteInOneCommit() {
        // Seed a throwaway order with two lines through the commit itself.
        Order seeded = assertInstanceOf(SaveResult.Committed.class, service.save(newOrder(
                new OrderLine(1, 41010, "Ski Boots", new BigDecimal("50.00"), 5, 5),
                new OrderLine(2, 20108, "Ski Pole", new BigDecimal("13.00"), 10, 10)))).order();
        long id = seeded.id();
        try {
            // One whole-graph save that at once UPDATEs line 1 (new shipped qty), DELETEs line 2, and
            // INSERTs a new line 3 — carrying the current scn so the header update matches.
            Order edited = new Order(id, seeded.customerId(), seeded.customerName(),
                    seeded.dateOrdered(), seeded.dateShipped(), seeded.salesRepId(), seeded.salesRepName(),
                    seeded.paymentType(), seeded.orderFilled(), seeded.scn(),
                    List.of(
                            new OrderLine(1, 41010, "Ski Boots", new BigDecimal("50.00"), 5, 3), // 3*50=150
                            new OrderLine(3, 30421, "Pro Ski Boot", new BigDecimal("1000.00"), 1, 1))); // 1*1000

            SaveResult result = service.save(edited);
            assertInstanceOf(SaveResult.Committed.class, result);

            Order reloaded = repository.loadGraph(id).orElseThrow();
            // Line 2 is gone, line 1 kept (updated), line 3 inserted — all in the one commit.
            assertEquals(List.of(1L, 3L), reloaded.lines().stream().map(OrderLine::itemId).toList());
            OrderLine one = reloaded.lines().get(0);
            assertEquals(3L, one.quantityShipped()); // the update landed
            assertEquals(30421L, reloaded.lines().get(1).productId()); // the insert landed
            assertEquals(0, new BigDecimal("1150.00").compareTo(reloaded.total()));
        } finally {
            deleteOrderGraph(id);
        }
    }

    @Test
    void staleScnMakesTheCommitFailWithoutOverwriting() {
        Order seeded = assertInstanceOf(SaveResult.Committed.class, service.save(newOrder(
                new OrderLine(1, 41010, "Ski Boots", new BigDecimal("50.00"), 5, 5)))).order();
        long id = seeded.id();
        long staleScn = seeded.scn();
        try {
            // A concurrent commit changes the row after our load — advancing its ORA_ROWSCN. We mark
            // the order filled, the winning change the stale save must not clobber.
            jdbcClient().sql("UPDATE s_ord SET order_filled = 'Y' WHERE id = :id").param("id", id).update();

            // Our save still carries the pre-conflict scn and a competing change (order_filled = N).
            Order stale = new Order(id, seeded.customerId(), seeded.customerName(),
                    seeded.dateOrdered(), seeded.dateShipped(), seeded.salesRepId(), seeded.salesRepName(),
                    seeded.paymentType(), false, staleScn, seeded.lines());

            SaveResult result = service.save(stale);

            // The scn-guarded update matched no row: the commit fails as a typed conflict...
            assertInstanceOf(SaveResult.Conflict.class, result);
            // ...and the concurrent change stands — no silent overwrite back to order_filled = N.
            assertTrue(repository.loadGraph(id).orElseThrow().orderFilled(),
                    "the concurrent change must survive; the stale save must not overwrite it");
        } finally {
            deleteOrderGraph(id);
        }
    }

    @Test
    void commitGateRefusesStillInvalidInputWithoutPersisting() {
        Order seeded = assertInstanceOf(SaveResult.Committed.class, service.save(newOrder(
                new OrderLine(1, 41010, "Ski Boots", new BigDecimal("50.00"), 5, 5)))).order();
        long id = seeded.id();
        try {
            // A ship date before the order date violates the WHEN-VALIDATE-RECORD guard.
            Order invalid = new Order(id, seeded.customerId(), seeded.customerName(),
                    LocalDate.of(1992, 8, 31), LocalDate.of(1992, 8, 1),
                    seeded.salesRepId(), seeded.salesRepName(),
                    seeded.paymentType(), seeded.orderFilled(), seeded.scn(), seeded.lines());

            SaveResult result = service.save(invalid);

            // The Service commit is the authoritative gate (ADR-0002): refused, and nothing persisted —
            // the order keeps its original (valid) ship date, untouched.
            assertInstanceOf(SaveResult.Invalid.class, result);
            assertEquals(seeded.dateShipped(), repository.loadGraph(id).orElseThrow().dateShipped());
        } finally {
            deleteOrderGraph(id);
        }
    }

    /** A new (unsaved) order for customer 201 — id/scn 0, valid dates, CASH — with the given lines. */
    private static Order newOrder(OrderLine... lines) {
        return new Order(0, 201, "Unisports", LocalDate.of(1992, 8, 31), null,
                1L, "Magee", "CASH", false, 0L, List.of(lines));
    }

    /** Removes a throwaway order and its lines directly, so the shared seed stays exactly as loaded
     * (the cascade guard would otherwise block deleting an order that still has lines). */
    private void deleteOrderGraph(long orderId) {
        if (orderId == 0) {
            return;
        }
        jdbcClient().sql("DELETE FROM s_item WHERE ord_id = :id").param("id", orderId).update();
        jdbcClient().sql("DELETE FROM s_ord WHERE id = :id").param("id", orderId).update();
    }
}
