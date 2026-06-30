package com.example.summit.service;

import com.example.summit.data.OrderRepository;
import com.example.summit.domain.CascadeDeleteBlockedException;
import com.example.summit.domain.Order;
import com.example.summit.domain.OrderSearchCriteria;
import com.example.summit.domain.OrderSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for orders — owns <em>load</em> and the transactional boundary. For
 * the read-only tracer bullet both operations are read-only; the whole-graph
 * {@code COMMIT_FORM} save ({@code transaction-commit-form}) lands in a later slice.
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
}
