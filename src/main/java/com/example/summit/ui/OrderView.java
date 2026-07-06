package com.example.summit.ui;

import com.example.summit.domain.Order;
import com.example.summit.domain.OrderSummary;
import com.example.summit.presenter.OrderPresenter;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * The Summit ORDERS view — the functional tracer bullet (issue #85). A clerk types a quick
 * search (order id / customer / sales rep), picks one suggestion, and that order's
 * <strong>header</strong> loads <strong>read-only</strong> into a {@link FormLayout},
 * wiring {@code View ← Presenter ← Service ← Domain ← Data} end to end.
 *
 * <p>The load is one {@code loadGraph} read whose SQL joins {@code S_CUSTOMER}/{@code S_EMP}
 * so the customer and rep names arrive with the row — the Forms {@code POST-QUERY} lookups
 * dissolved, no N+1.
 *
 * <p>Read-only is a <em>state</em>, not a component type (ADR-0013): every header field
 * renders as the control its kind maps to — a {@link DatePicker} for a date, a {@link
 * RadioButtonGroup} for the Cash/Credit radio, a {@link Checkbox} for the Y/N flag — put
 * into {@code setReadOnly(true)}, never flattened to a text dump. Fields are placed in the
 * form's order (ADR-0012, from the spec {@code layoutPlan}). This slice is the read-only
 * header only: editing is V9; the bordered group + section title chrome is a later slice
 * (#87), so the header is one flat {@code FormLayout} here.
 */
@Route(value = "orders", layout = MainLayout.class)
@PageTitle("Orders | Summit Migration POC")
public class OrderView extends VerticalLayout {

    private final OrderPresenter presenter;

    // Header fields — one per in-scope S_ORD item, in layoutPlan order (ADR-0012).
    private final TextField id = new TextField("Order Id");
    private final DatePicker dateOrdered = new DatePicker("Date Ordered");
    private final TextField customerId = new TextField("Customer Id");
    private final TextField customerName = new TextField("Customer Name");
    private final TextField salesRepId = new TextField("Sales Rep Id");
    private final TextField salesRepName = new TextField("Sales Rep Name");
    private final DatePicker dateShipped = new DatePicker("Date Shipped");
    private final Checkbox orderFilled = new Checkbox("Order Filled");
    private final RadioButtonGroup<String> paymentType = new RadioButtonGroup<>("Payment Type");

    private final FormLayout header = new FormLayout();

    public OrderView(OrderPresenter presenter) {
        this.presenter = presenter;

        add(buildSearch(), header);
        buildHeader();

        // Search-first empty state: nothing is loaded until a suggestion is picked
        // (FORM.WHEN-NEW-FORM-INSTANCE re-decided to search-first, ledger #89/#85).
        header.setVisible(false);
    }

    private ComboBox<OrderSummary> buildSearch() {
        ComboBox<OrderSummary> search = new ComboBox<>("Quick search");
        search.setPlaceholder("Order id, customer, or sales rep");
        search.setWidth("22em");
        // Server-side filtering: each keystroke runs the repository's joined search query
        // (Forms KEY-EXEQRY quick search), so suggestions come from the DB, not the client.
        search.setItems(query ->
                presenter.search(query.getFilter().orElse("")).stream()
                        .skip(query.getOffset())
                        .limit(query.getLimit()));
        search.setItemLabelGenerator(OrderView::suggestionLabel);
        search.addValueChangeListener(event -> {
            OrderSummary picked = event.getValue();
            if (picked == null) {
                header.setVisible(false);
            } else {
                presenter.load(picked.id()).ifPresent(this::showHeader);
            }
        });
        return search;
    }

    /** The autocomplete label a clerk recognises: {@code "50001 — Unisports (Magee)"}. */
    static String suggestionLabel(OrderSummary o) {
        StringBuilder label = new StringBuilder().append(o.id());
        if (o.customerName() != null) {
            label.append(" — ").append(o.customerName());
        }
        if (o.salesRepName() != null) {
            label.append(" (").append(o.salesRepName()).append(')');
        }
        return label.toString();
    }

    private void buildHeader() {
        paymentType.setItems("CASH", "CREDIT");
        paymentType.setItemLabelGenerator(value -> "CREDIT".equals(value) ? "Credit" : "Cash");

        // Every field is read-only in this slice — a state on the mapped control, not a
        // switch to a plainer control (ADR-0013). setReadOnly is the control's own
        // affordance; the control kind (TextField/DatePicker/Checkbox/RadioButtonGroup) is
        // unchanged, so making it editable in V9 is flipping this state, not swapping types.
        id.setReadOnly(true);
        dateOrdered.setReadOnly(true);
        customerId.setReadOnly(true);
        customerName.setReadOnly(true);
        salesRepId.setReadOnly(true);
        salesRepName.setReadOnly(true);
        dateShipped.setReadOnly(true);
        orderFilled.setReadOnly(true);
        paymentType.setReadOnly(true);

        // Stable ids let the layout-fidelity gate assert the rendered field order
        // (ADR-0012) directly against the form's DOM children.
        id.setId("order-id");
        dateOrdered.setId("date-ordered");
        customerId.setId("customer-id");
        customerName.setId("customer-name");
        salesRepId.setId("sales-rep-id");
        salesRepName.setId("sales-rep-name");
        dateShipped.setId("date-shipped");
        orderFilled.setId("order-filled");
        paymentType.setId("payment-type");

        // Fields are added in the form's order (ADR-0012, from the spec layoutPlan).
        header.add(id, dateOrdered, customerId, customerName,
                salesRepId, salesRepName, dateShipped, orderFilled, paymentType);
        header.setId("order-header");
    }

    private void showHeader(Order order) {
        id.setValue(String.valueOf(order.id()));
        customerId.setValue(String.valueOf(order.customerId()));
        customerName.setValue(nullToEmpty(order.customerName()));
        dateOrdered.setValue(order.dateOrdered());
        dateShipped.setValue(order.dateShipped());
        salesRepId.setValue(order.salesRepId() == null ? "" : String.valueOf(order.salesRepId()));
        salesRepName.setValue(nullToEmpty(order.salesRepName()));
        paymentType.setValue(order.paymentType());
        orderFilled.setValue(order.orderFilled());
        header.setVisible(true);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
