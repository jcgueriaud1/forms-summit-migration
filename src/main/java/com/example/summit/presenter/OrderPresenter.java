package com.example.summit.presenter;

import com.example.summit.domain.Order;
import com.example.summit.domain.OrderSearchResult;
import com.example.summit.service.OrderService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Presenter layer for ORDERS — owns <em>orchestration</em>. The view's quick-search
 * ComboBox calls {@link #search(String)} as the clerk types, and its value-change
 * listener calls {@link #load(long)} when a suggestion is picked; the presenter
 * returns view-ready domain objects. It does not hold the load SQL (that is the
 * Service/Data) nor any formula (that is the Domain) — here it guards the search term
 * so an empty type-ahead never hits the database.
 */
@Component
public class OrderPresenter {

    private final OrderService orderService;

    public OrderPresenter(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Quick-search suggestions for a type-ahead term. A blank/whitespace term returns
     * no suggestions without querying — the ComboBox's initial empty filter must not
     * pull the whole table.
     */
    public List<OrderSearchResult> search(String term) {
        if (term == null || term.isBlank()) {
            return List.of();
        }
        return orderService.search(term);
    }

    /** Loads the chosen order's header (single joined read). Empty if it vanished. */
    public Optional<Order> load(long id) {
        return orderService.load(id);
    }
}
