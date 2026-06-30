package com.example.summit.service;

import com.example.summit.data.OrderRepository;
import com.example.summit.data.SummitDbIT;
import com.example.summit.domain.CascadeDeleteBlockedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the {@code S_ORD_S_ITEM} <strong>Non-Isolated</strong> cascade-delete guard
 * ({@code delete-cascade-guard}, issue #16) against a real Summit Oracle. The headline: deleting an
 * order that still has line items is vetoed with a {@link CascadeDeleteBlockedException} and the
 * order survives — {@code S_ITEM} detail rows can never be orphaned, the web counterpart of Forms'
 * {@code ON-CHECK-DELETE-MASTER}. An order with no lines deletes cleanly, so the guard blocks the
 * dangerous case without forbidding the safe one.
 *
 * <p>The {@code @Transactional} boundary is a no-op here (no Spring proxy on a hand-wired service),
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
}
