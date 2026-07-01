package com.example.summit.presenter;

import com.example.summit.domain.Order;
import com.example.summit.domain.OrderSearchCriteria;
import com.example.summit.domain.OrderSummary;
import com.example.summit.service.OrderService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Presenter layer — owns <em>orchestration</em>. It wraps the clerk's typed term in an
 * {@link OrderSearchCriteria} for the autocomplete and loads whichever order the clerk
 * picks. It calls the Service only; it holds no SQL and no business formula.
 */
@Component
public class OrderPresenter {

    private final OrderService orderService;

    public OrderPresenter(OrderService orderService) {
        this.orderService = orderService;
    }

    /** Quick-search suggestions for the term the clerk has typed so far. */
    public List<OrderSummary> suggest(String quickTerm) {
        return orderService.search(new OrderSearchCriteria(quickTerm));
    }

    /** Loads the picked order's header, with customer and sales-rep names joined. */
    public Optional<Order> load(long orderId) {
        return orderService.load(orderId);
    }
}
