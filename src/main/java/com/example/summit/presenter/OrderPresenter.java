package com.example.summit.presenter;

import com.example.summit.domain.Order;
import com.example.summit.domain.OrderSummary;
import com.example.summit.service.OrderService;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Presenter for the ORDERS view — owns orchestration between the view and the service,
 * keeping the view free of the service/transaction boundary. In this slice it runs the
 * quick search and loads the chosen order; later slices grow it to own prev/next
 * position, totals recompute, and commit-outcome mapping (PRD).
 */
@Component
public class OrderPresenter {

    private final OrderService orderService;

    public OrderPresenter(OrderService orderService) {
        this.orderService = orderService;
    }

    /** Autocomplete suggestions for the quick-search term. */
    public List<OrderSummary> search(String term) {
        return orderService.search(term);
    }

    /** Loads the header of the picked order, if it still exists. */
    public Optional<Order> load(int orderId) {
        return orderService.load(orderId);
    }
}
