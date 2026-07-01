package com.example.summit.service;

import com.example.summit.data.OrderRepository;
import com.example.summit.domain.CascadeDeleteBlockedException;
import com.example.summit.domain.CommitResult;
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
 * Service layer for orders — owns <em>load</em>, <em>delete</em>, and the whole-graph
 * <em>commit</em>, and the transactional boundary around each.
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

    /** Loads one order's header with its display names joined in a single round-trip. */
    @Transactional(readOnly = true)
    public Optional<Order> load(long orderId) {
        return orderRepository.loadGraph(orderId);
    }

    /**
     * Deletes an order, enforcing the {@code S_ORD_S_ITEM} <strong>Non-Isolated</strong>
     * cascade-delete guard ({@code delete-cascade-guard}, issue #16): the order is loaded first,
     * and if it still {@link Order#hasLineItems() has line items} the delete is vetoed with a
     * {@link CascadeDeleteBlockedException} — so {@code S_ITEM} detail rows are never orphaned, the
     * web counterpart of Forms' {@code ON-CHECK-DELETE-MASTER}. The guard decision is the domain's
     * ({@code Order.hasLineItems}, ADR-0001); this Service method is the gate that acts on it.
     * Deleting an unknown order is a no-op.
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

    /**
     * The {@code COMMIT_FORM} whole-graph save ({@code transaction-commit-form}, issue #19): the
     * header and all its line items — inserts, updates and line deletes — persist together as one
     * {@code @Transactional} unit. This method is the authoritative validation gate (ADR-0002):
     * anything still invalid is refused here, so nothing bad ever persists.
     *
     * <p>The sequence:
     * <ol>
     *   <li><b>Gate.</b> The domain ship-date invariant ({@link Order#shipDateValid()}, the
     *       {@code WHEN-VALIDATE-RECORD} guard) is re-checked authoritatively; a violation is
     *       {@link CommitResult.Rejected} with nothing written.</li>
     *   <li><b>New order</b> ({@link Order#isNew()}): the header is inserted with an id from
     *       {@code S_ORD_ID.nextval} (the {@code PRE-INSERT}), then every line is inserted.</li>
     *   <li><b>Existing order</b>: the header {@code UPDATE} carries the {@code ORA_ROWSCN} guard and
     *       runs <em>first</em>, so it doubles as the optimistic lock — {@code 0} rows updated means
     *       a concurrent change since load, returned as {@link CommitResult.Conflict} <em>before any
     *       line is touched</em> (no partial overwrite). Otherwise the lines are reconciled against
     *       what is persisted.</li>
     * </ol>
     *
     * <p>Every line {@code INSERT} fires the DB {@code AUDIT_S_ITEM} trigger inside this same
     * transaction, unchanged and with zero Java (a clean DB-boundary proof point,
     * {@code orders-form-spec.md}).
     */
    @Transactional
    public CommitResult save(Order order) {
        // The commit gate — authoritative, delegating to the domain invariant (ADR-0001/0002).
        if (!order.shipDateValid()) {
            return new CommitResult.Rejected("Ship date cannot be before the order date.");
        }
        if (order.isNew()) {
            long newId = orderRepository.insertOrder(order);
            order.lines().forEach(line -> orderRepository.insertItem(newId, line));
            return new CommitResult.Committed(newId);
        }
        // The ORA_ROWSCN-guarded header UPDATE is the optimistic lock: 0 rows = a concurrent change
        // since load. Running it first means a conflict aborts before any line write — nothing is
        // overwritten and (in production) the @Transactional boundary leaves no partial state.
        if (orderRepository.updateOrderHeader(order) == 0) {
            return new CommitResult.Conflict();
        }
        reconcileLines(order.id(), order.lines());
        return new CommitResult.Committed(order.id());
    }

    /**
     * Reconciles an order's persisted lines with the working set: deletes lines no longer present,
     * updates those that remain, inserts those newly added. Deletes run first so a freed
     * {@code (ord_id, product_id)} slot can never collide with an insert/update in the same commit.
     */
    private void reconcileLines(long orderId, List<OrderLine> desired) {
        Set<Long> currentIds = new HashSet<>(orderRepository.currentItemIds(orderId));
        Set<Long> desiredIds = desired.stream().map(OrderLine::itemId).collect(Collectors.toSet());

        currentIds.stream()
                .filter(id -> !desiredIds.contains(id))
                .forEach(id -> orderRepository.deleteItem(orderId, id));

        for (OrderLine line : desired) {
            if (currentIds.contains(line.itemId())) {
                orderRepository.updateItem(orderId, line);
            } else {
                orderRepository.insertItem(orderId, line);
            }
        }
    }
}
