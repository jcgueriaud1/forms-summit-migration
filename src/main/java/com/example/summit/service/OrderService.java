package com.example.summit.service;

import com.example.summit.data.OrderRepository;
import com.example.summit.domain.Order;
import com.example.summit.domain.OrderSearchCriteria;
import com.example.summit.domain.OrderSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for orders — owns <em>load</em> and the transactional boundary. Both
 * operations in the read-only tracer bullet (#39) are read-only reads; the whole-graph
 * {@code COMMIT_FORM} save under the {@code ORA_ROWSCN} check ({@code
 * transaction-commit-form}) arrives in a later slice (#48).
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
}
