package com.example.summit.ui;

import com.example.summit.domain.Order;
import com.example.summit.domain.OrderSummary;
import com.example.summit.presenter.OrderPresenter;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * The ORDERS view — the first real form migration (PRD #8, Step 1a). A clerk quick-
 * searches by order id / customer / sales rep, picks a suggestion, and that one
 * {@code S_ORD} order loads into a <strong>read-only header {@link FormLayout}</strong>.
 *
 * <p>This is the faithful web rendering of Forms' single-record Enter-Query on the
 * {@code S_ORD} master (Variant A, {@code block-master-single}): a lookup that loads
 * one record, not a grid of orders. Editing, the line-items grid, totals, the pager
 * and commit arrive in later slices. The view holds no SQL — it talks only to the
 * {@link OrderPresenter} (View &larr; Presenter &larr; Service &larr; Data).
 */
@Route(value = "orders", layout = MainLayout.class)
@PageTitle("Orders | Summit Migration POC")
public class OrderView extends VerticalLayout {

    private final OrderPresenter presenter;

    private final TextField orderId = new TextField("Order Id");
    private final TextField customerId = new TextField("Customer Id");
    private final TextField customerName = new TextField("Customer Name");
    private final DatePicker dateOrdered = new DatePicker("Date Ordered");
    private final DatePicker dateShipped = new DatePicker("Date Shipped");
    private final TextField salesRepId = new TextField("Sales Rep Id");
    private final TextField salesRepName = new TextField("Sales Rep Name");
    private final TextField paymentType = new TextField("Payment Type");
    private final Checkbox orderFilled = new Checkbox("Order Filled");

    private final FormLayout header = new FormLayout();
    private final Paragraph emptyHint = new Paragraph("Search for an order to load its header.");

    public OrderView(OrderPresenter presenter) {
        this.presenter = presenter;

        add(buildSearch(), emptyHint, header);
        buildHeader();
        header.setVisible(false);
    }

    private ComboBox<OrderSummary> buildSearch() {
        ComboBox<OrderSummary> search = new ComboBox<>("Find order");
        search.setWidth("420px");
        search.setPlaceholder("Order id, customer, or sales rep");
        search.setItemLabelGenerator(OrderView::suggestionLabel);
        // Server-side autocomplete: each keystroke runs the quick search through the presenter.
        search.setItems(query -> presenter.suggest(query.getFilter().orElse("")).stream());
        search.addValueChangeListener(event -> {
            OrderSummary picked = event.getValue();
            if (picked == null) {
                clearHeader();
            } else {
                presenter.load(picked.id()).ifPresent(this::showOrder);
            }
        });
        return search;
    }

    private static String suggestionLabel(OrderSummary s) {
        String rep = s.salesRepName() == null ? "no rep" : s.salesRepName();
        return "#" + s.id() + " — " + s.customerName() + " (" + rep + ")";
    }

    private void buildHeader() {
        orderId.setReadOnly(true);
        customerId.setReadOnly(true);
        customerName.setReadOnly(true);
        dateOrdered.setReadOnly(true);
        dateShipped.setReadOnly(true);
        salesRepId.setReadOnly(true);
        salesRepName.setReadOnly(true);
        paymentType.setReadOnly(true);
        orderFilled.setReadOnly(true);

        header.add(orderId, customerId, customerName,
                dateOrdered, dateShipped,
                salesRepId, salesRepName,
                paymentType, orderFilled);
    }

    private void showOrder(Order order) {
        orderId.setValue(String.valueOf(order.id()));
        customerId.setValue(String.valueOf(order.customerId()));
        customerName.setValue(nullSafe(order.customerName()));
        dateOrdered.setValue(order.dateOrdered());
        dateShipped.setValue(order.dateShipped());
        salesRepId.setValue(order.salesRepId() == null ? "" : String.valueOf(order.salesRepId()));
        salesRepName.setValue(nullSafe(order.salesRepName()));
        paymentType.setValue(nullSafe(order.paymentType()));
        orderFilled.setValue(order.orderFilled());

        emptyHint.setVisible(false);
        header.setVisible(true);
    }

    private void clearHeader() {
        header.setVisible(false);
        emptyHint.setVisible(true);
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
