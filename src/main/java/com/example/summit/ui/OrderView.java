package com.example.summit.ui;

import com.example.summit.domain.OrderSummary;
import com.example.summit.presenter.OrderPresenter;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * The ORDERS view (issue #85, PRD step 1a) — the functional tracer bullet wiring
 * View &larr; Presenter &larr; Service &larr; Domain &larr; Data end to end for the
 * first time.
 *
 * <p>The clerk types a quick-search term (order id, customer name, or sales rep) into
 * an autocomplete {@link ComboBox}; picking one suggestion loads that single order's
 * header into the read-only {@link OrderHeaderForm}. This is Variant A (resolved in the
 * search-UX prototype): a lookup that loads one record, not a grid of orders.
 *
 * <p>The app-shell chrome (the "Order Information" heading, the bordered group, the
 * line-items grid, prev/next, advanced search) belongs to later slices; this slice is
 * deliberately just search &rarr; read-only header.
 */
@Route(value = "orders", layout = MainLayout.class)
@PageTitle("Orders | Summit Migration POC")
public class OrderView extends VerticalLayout {

    private final ComboBox<OrderSummary> quickSearch = new ComboBox<>("Search orders");
    private final OrderHeaderForm header = new OrderHeaderForm();

    public OrderView(OrderPresenter presenter) {
        quickSearch.setPlaceholder("Order id, customer, or sales rep");
        quickSearch.setItemLabelGenerator(OrderView::suggestionLabel);
        quickSearch.setWidth("22rem");
        // Lazy autocomplete: the typed term becomes the search filter, matched
        // server-side across id / customer / sales rep on a single joined read.
        quickSearch.setItems(query -> presenter.search(query.getFilter().orElse(null))
                .stream()
                .skip(query.getOffset())
                .limit(query.getLimit()));
        quickSearch.addValueChangeListener(event -> {
            OrderSummary selected = event.getValue();
            if (selected == null) {
                header.setOrder(null);
            } else {
                presenter.load(selected.id()).ifPresent(header::setOrder);
            }
        });

        add(quickSearch, header);
    }

    private static String suggestionLabel(OrderSummary summary) {
        String label = summary.id() + " — " + summary.customerName();
        if (summary.salesRepName() != null && !summary.salesRepName().isBlank()) {
            label += " — " + summary.salesRepName();
        }
        return label;
    }
}
