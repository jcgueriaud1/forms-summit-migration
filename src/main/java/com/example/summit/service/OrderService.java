package com.example.summit.service;

import com.example.summit.data.OrderRepository;
import com.example.summit.domain.Order;
import com.example.summit.domain.OrderSummary;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer for the ORDERS view — the transactional boundary. In this slice both
 * operations are read-only loads; later slices add the whole-graph {@code save} under
 * the {@code ORA_ROWSCN} check (the Forms {@code COMMIT_FORM} gate).
 */
@Service
public class OrderService {

    private final OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    /** Quick search across order id / customer name / sales-rep name. */
    @Transactional(readOnly = true)
    public List<OrderSummary> search(String term) {
        return repository.search(term);
    }

    /** Loads one order's header (with joined display names) for the chosen suggestion. */
    @Transactional(readOnly = true)
    public Optional<Order> load(int orderId) {
        return repository.loadGraph(orderId);
    }
}
