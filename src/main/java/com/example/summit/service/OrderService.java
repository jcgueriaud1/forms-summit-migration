package com.example.summit.service;

import com.example.summit.data.OrderRepository;
import com.example.summit.domain.CascadeDeleteBlockedException;
import com.example.summit.domain.Order;
import com.example.summit.domain.OrderLine;
import com.example.summit.domain.OrderSearchCriteria;
import com.example.summit.domain.OrderSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service layer for orders — owns <em>load</em> and the transactional boundary, and the
 * <em>whole-graph commit</em> that is the climax of the migration (issue #48): one
 * {@code @Transactional} {@link #save(Order)} persists the {@code S_ORD} header and all its
 * {@code S_ITEM} lines — inserts, updates, and deletes — as a single unit
 * ({@code transaction-commit-form}), the web mapping of Forms' {@code COMMIT_FORM}.
 *
 * <p>Concurrency is optimistic ({@code concurrency-pessimistic-to-scn}): the header's
 * {@code ORA_ROWSCN}, read on load, is re-checked in a guarded {@code UPDATE}; a change since load
 * makes the commit fail with a {@link SaveResult.Conflict} rather than silently overwriting the
 * concurrent change — no pessimistic across-think-time lock. The commit is also the
 * <strong>authoritative validation gate</strong> (ADR-0002): still-invalid input is refused with a
 * {@link SaveResult.Invalid} before any write, so nothing bad persists. The DB {@code AUDIT_S_ITEM}
 * trigger fires inside this transaction unchanged, on each line insert.
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /** Runs the quick search and returns the matching orders (the autocomplete suggestions). */
    @Transactional(readOnly = true)
    public List<OrderSummary> search(OrderSearchCriteria criteria) {
        return orderRepository.findByCriteria(criteria);
    }

    /** Loads one order's header with its customer and sales-rep names joined in a single round-trip. */
    @Transactional(readOnly = true)
    public Optional<Order> load(long orderId) {
        return orderRepository.loadGraph(orderId);
    }

    /**
     * The whole-graph {@code COMMIT_FORM} save (issue #48) — persists the header and every line as
     * one transactional unit and returns a {@link SaveResult} describing the outcome.
     *
     * <ul>
     *   <li><strong>Validation gate first (authoritative).</strong> Still-invalid input — here the
     *       {@code WHEN-VALIDATE-RECORD} ship-date guard ({@link Order#shipDateValid()}) — is refused
     *       with {@link SaveResult#invalid} <em>before any write</em>, so nothing bad persists
     *       (ADR-0002).</li>
     *   <li><strong>New order.</strong> Its id is drawn from {@code S_ORD_ID.nextval} (the header
     *       {@code PRE-INSERT}), the header is inserted, and every line is inserted under it.</li>
     *   <li><strong>Existing order.</strong> The scn-guarded header {@code UPDATE} runs
     *       <em>first</em>; if it matches no row the {@code ORA_ROWSCN} moved since load, so we
     *       return {@link SaveResult#conflict} having written nothing — the stale save fails rather
     *       than overwriting the concurrent change. Otherwise the lines are reconciled against the
     *       persisted set: removed lines deleted, kept lines updated, new lines inserted.</li>
     * </ul>
     *
     * <p>On success the saved graph is reloaded and returned in the {@link SaveResult.Committed}, so
     * the caller holds the new id and the advanced {@code ORA_ROWSCN} for the next commit.
     */
    @Transactional
    public SaveResult save(Order order) {
        if (!order.shipDateValid()) {
            return SaveResult.invalid("Ship date cannot be before the order date.");
        }
        if (order.isNew()) {
            long newId = orderRepository.nextOrderId();
            orderRepository.insertHeader(order.withId(newId));
            for (OrderLine line : order.lines()) {
                orderRepository.insertLine(newId, line);
            }
            return SaveResult.committed(orderRepository.loadGraph(newId).orElseThrow());
        }
        // The scn-guarded header update is the FIRST write, so a conflict (0 rows) leaves the DB
        // untouched — we can surface it without a partial save to undo (no silent overwrite).
        if (orderRepository.updateHeaderIfUnchanged(order) == 0) {
            return SaveResult.conflict();
        }
        reconcileLines(order);
        return SaveResult.committed(orderRepository.loadGraph(order.id()).orElseThrow());
    }

    /**
     * Reconciles an existing order's persisted {@code S_ITEM} lines to the working set: lines that no
     * longer appear are deleted, lines that still exist are updated in place, and lines with a new
     * per-order {@code item_id} are inserted — the detail half of the whole-graph commit.
     */
    private void reconcileLines(Order order) {
        Set<Long> desired = order.lines().stream()
                .map(OrderLine::itemId)
                .collect(Collectors.toSet());
        Set<Long> persisted = new HashSet<>(orderRepository.existingItemIds(order.id()));

        for (Long itemId : persisted) {
            if (!desired.contains(itemId)) {
                orderRepository.deleteLine(order.id(), itemId);
            }
        }
        for (OrderLine line : order.lines()) {
            if (persisted.contains(line.itemId())) {
                orderRepository.updateLine(order.id(), line);
            } else {
                orderRepository.insertLine(order.id(), line);
            }
        }
    }

    /**
     * Deletes an order, enforcing the {@code S_ORD_S_ITEM} <strong>Non-Isolated</strong>
     * cascade-delete guard ({@code delete-cascade-guard}, issue #45): the order is loaded first, and
     * if it still {@link Order#hasLineItems() has line items} the delete is vetoed with a
     * {@link CascadeDeleteBlockedException} — so {@code S_ITEM} detail rows are never orphaned, the
     * web counterpart of Forms' {@code ON-CHECK-DELETE-MASTER}. The guard <em>decision</em> is the
     * Domain's ({@link Order#hasLineItems}, ADR-0001); this Service method is the gate that acts on
     * it. Deleting an unknown order is a no-op.
     */
    @Transactional
    public void delete(long orderId) {
        Optional<Order> order = orderRepository.loadGraph(orderId);
        if (order.isEmpty()) {
            return;
        }
        if (order.get().hasLineItems()) {
            throw new CascadeDeleteBlockedException(
                    "Cannot delete order #" + orderId + " while it still has line items.");
        }
        orderRepository.deleteOrder(orderId);
    }
}
