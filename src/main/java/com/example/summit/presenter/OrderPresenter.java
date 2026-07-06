package com.example.summit.presenter;

import com.example.summit.domain.Order;
import com.example.summit.domain.OrderSummary;
import com.example.summit.service.OrderService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Presenter layer for the ORDERS view — owns <em>orchestration</em>. It answers the view's
 * two questions in this slice (issue #85): what suggestions match a quick-search term, and
 * what one order's header is. It holds no formula and no SQL — it delegates to the Service
 * and hands view-ready domain types back (ADR-0001). As the view gains editing (V9+), the
 * field listeners and defaulters that mutate the in-memory order land here.
 */
@Component
public class OrderPresenter {

    private final OrderService orderService;

    public OrderPresenter(OrderService orderService) {
        this.orderService = orderService;
    }

    /** Suggestions for the quick-search autocomplete. */
    public List<OrderSummary> search(String term) {
        return orderService.search(term);
    }

    /** Loads the header of the chosen order, if it still exists. */
    public Optional<Order> load(long id) {
        return orderService.loadGraph(id);
    }
}
