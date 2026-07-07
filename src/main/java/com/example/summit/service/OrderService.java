package com.example.summit.service;

import com.example.summit.data.OrderRepository;
import com.example.summit.domain.Order;
import com.example.summit.domain.OrderSearchResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for ORDERS — owns <em>load</em>, the transactional boundary. This
 * slice is read-only: quick-search suggestions and the single-order header load, each
 * a {@code readOnly} transaction. The whole-graph save under the {@code ORA_ROWSCN}
 * check (the Forms {@code COMMIT_FORM} gate) arrives with the editable slice (V9).
 */
@Service
public class OrderService {

    private final OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<OrderSearchResult> search(String term) {
        return repository.search(term);
    }

    @Transactional(readOnly = true)
    public Optional<Order> load(long id) {
        return repository.loadGraph(id);
    }
}
