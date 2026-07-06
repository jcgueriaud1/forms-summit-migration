package com.example.summit.ui;

import com.example.summit.domain.Order;
import com.example.summit.domain.PaymentType;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;

/**
 * The {@code S_ORD} header rendered as a read-only {@link FormLayout} — the "Order
 * Information" fields the clerk reads at a glance (issue #85, PRD step 1a).
 *
 * <p><strong>Field order (ADR-0012).</strong> Fields are added in the form's own order
 * (the spec {@code layoutPlan}: id; date-ordered, customer id/name; sales-rep id/name;
 * date-shipped, order-filled; payment-type) — never re-ordered. Each field carries a
 * {@code data-field} marker so the layout-fidelity test can assert that order.
 *
 * <p><strong>Read-only is a state, not a type (ADR-0013).</strong> Every field renders
 * as its <em>mapped</em> control kind — payment type a {@link RadioButtonGroup}, order
 * filled a {@link Checkbox}, dates {@link DatePicker}s — put into a read-only state via
 * {@code setReadOnly(true)}. Nothing is flattened to a text dump. The section title,
 * bordered group chrome, and making fields editable are later slices (#87 / V9).
 */
public class OrderHeaderForm extends FormLayout {

    private final TextField orderId = new TextField("Order Id");
    private final DatePicker dateOrdered = new DatePicker("Date Ordered");
    private final TextField customerId = new TextField("Customer Id");
    private final TextField customerName = new TextField("Customer Name");
    private final TextField salesRepId = new TextField("Sales Rep Id");
    private final TextField salesRepName = new TextField("Sales Rep Name");
    private final DatePicker dateShipped = new DatePicker("Date Shipped");
    private final Checkbox orderFilled = new Checkbox("Order Filled");
    private final RadioButtonGroup<PaymentType> paymentType = new RadioButtonGroup<>("Payment Type");

    private final Binder<Order> binder = new Binder<>();

    public OrderHeaderForm() {
        setId("order-header-form");

        paymentType.setItems(PaymentType.values());
        paymentType.setItemLabelGenerator(PaymentType::label);

        // Added in the form's field order (layoutPlan, ADR-0012).
        addField("order-id", orderId);
        addField("date-ordered", dateOrdered);
        addField("customer-id", customerId);
        addField("customer-name", customerName);
        addField("sales-rep-id", salesRepId);
        addField("sales-rep-name", salesRepName);
        addField("date-shipped", dateShipped);
        addField("order-filled", orderFilled);
        addField("payment-type", paymentType);

        bindReadOnly();
        setVisible(false); // nothing selected yet
    }

    private void addField(String key, Component field) {
        field.getElement().setAttribute("data-field", key);
        add(field);
    }

    private void bindReadOnly() {
        // Read-only bindings (null setter) populate the mapped control from the loaded
        // Order; the id fields are shown as their plain numeric text (a PK/FK display).
        binder.forField(orderId).bind(o -> asText(o.id()), null);
        binder.forField(customerId).bind(o -> asText(o.customerId()), null);
        binder.forField(customerName).bind(Order::customerName, null);
        binder.forField(salesRepId).bind(o -> asText(o.salesRepId()), null);
        binder.forField(salesRepName).bind(Order::salesRepName, null);
        binder.forField(dateOrdered).bind(Order::dateOrdered, null);
        binder.forField(dateShipped).bind(Order::dateShipped, null);
        binder.forField(paymentType).bind(Order::paymentType, null);
        binder.forField(orderFilled).bind(Order::orderFilled, null);

        // ADR-0013: read-only STATE on each mapped control, never a component swap.
        orderId.setReadOnly(true);
        dateOrdered.setReadOnly(true);
        customerId.setReadOnly(true);
        customerName.setReadOnly(true);
        salesRepId.setReadOnly(true);
        salesRepName.setReadOnly(true);
        dateShipped.setReadOnly(true);
        orderFilled.setReadOnly(true);
        paymentType.setReadOnly(true);
    }

    /** Populates the header from the loaded order, or hides it when nothing is selected. */
    public void setOrder(Order order) {
        binder.readBean(order);
        setVisible(order != null);
    }

    private static String asText(Integer value) {
        return value == null ? "" : value.toString();
    }
}
