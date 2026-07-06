package com.example.summit.service;

import com.example.summit.data.OrderRepository;
import com.example.summit.domain.Order;
import com.example.summit.domain.OrderSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for the {@code S_ORD} master — owns <em>load</em>, the transactional
 * boundary. For the ORDERS tracer bullet (issue #85) both operations are read-only: the
 * quick-search suggestion query and the single-read header load. Later slices grow the
 * commit side (whole-graph save under the {@code ORA_ROWSCN} check — the Forms {@code
 * COMMIT_FORM} gate) here.
 */
@Service
public class OrderService {

    private final OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    /** Quick-search suggestions for {@code term} (order id / customer / sales rep). */
    @Transactional(readOnly = true)
    public List<OrderSummary> search(String term) {
        return repository.search(term);
    }

    /** Loads one order's header — the customer and rep names arrive on this one read. */
    @Transactional(readOnly = true)
    public Optional<Order> loadGraph(long id) {
        return repository.loadGraph(id);
    }
}
