package com.example.summit.ui;

import com.example.summit.domain.Order;
import com.example.summit.domain.OrderSearchResult;
import com.example.summit.presenter.OrderPresenter;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * ORDERS view — the functional tracer bullet (issue #85). A clerk types a quick
 * search (order id, customer name, or sales rep) into the ComboBox, picks one
 * suggestion, and that order's <strong>header</strong> loads read-only through
 * View &larr; Presenter &larr; Service &larr; Domain &larr; Data.
 *
 * <p>The header is <em>read-only as a state, not a type</em> (ADR-0013): every field
 * renders as the component the Forms item mapped to — {@code DATE_ORDERED}/
 * {@code DATE_SHIPPED} stay {@link DatePicker}s, {@code ORDER_FILLED} a
 * {@link Checkbox}, {@code PAYMENT_TYPE} a {@link RadioButtonGroup} — each put into a
 * read-only state, never flattened to a {@link TextField} print-out. Fields are added
 * in the form's order (from the analyzer's layoutPlan, ADR-0012), under the
 * "Order Information" title the Forms frame drew. Making the header editable is V9;
 * this slice is read-only.
 */
@Route(value = "orders", layout = MainLayout.class)
@PageTitle("Orders | Summit Migration POC")
public class OrderView extends VerticalLayout {

    private final OrderPresenter presenter;

    // Header fields, kept as members so the value-change listener can populate them.
    // Control kind mirrors the Forms item type (ADR-0013), not "read-only ⇒ text".
    private final TextField orderId = new TextField("Order Id");
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

        add(searchBox(), new H2("Order Information"), buildReadOnlyHeader());
    }

    private ComboBox<OrderSearchResult> searchBox() {
        ComboBox<OrderSearchResult> search = new ComboBox<>("Find order");
        search.setId("order-search");
        search.setPlaceholder("Order id, customer, or sales rep");
        search.setClearButtonVisible(true);
        search.setItemLabelGenerator(OrderSearchResult::label);
        // Lazy fetch: the presenter guards a blank filter (no whole-table pull), and
        // the query is a joined server-side search (Forms KEY-EXEQRY → quick search).
        // The lazy Query's offset/limit must be consumed or Vaadin rejects the stream
        // as a data-provider contract breach. The presenter already caps results, so
        // this only pages the (small) result set the overlay asked for.
        search.setItems(query -> presenter.search(query.getFilter().orElse(""))
                .stream().skip(query.getOffset()).limit(query.getLimit()));
        search.addValueChangeListener(e -> {
            OrderSearchResult picked = e.getValue();
            if (picked == null) {
                clearHeader();
            } else {
                presenter.load(picked.id()).ifPresentOrElse(this::showOrder, this::clearHeader);
            }
        });
        return search;
    }

    private FormLayout buildReadOnlyHeader() {
        paymentType.setItems("CASH", "CREDIT");
        paymentType.setItemLabelGenerator(value -> "CASH".equals(value) ? "Cash" : "Credit");

        // Distinct ids let the layout-fidelity UI test assert the form's field order
        // (ADR-0012) by reading the light-DOM child order of the FormLayout.
        orderId.setId("field-order-id");
        dateOrdered.setId("field-date-ordered");
        customerId.setId("field-customer-id");
        customerName.setId("field-customer-name");
        salesRepId.setId("field-sales-rep-id");
        salesRepName.setId("field-sales-rep-name");
        dateShipped.setId("field-date-shipped");
        orderFilled.setId("field-order-filled");
        paymentType.setId("field-payment-type");

        // Field order = the form's order (layoutPlan, ADR-0012); never invented.
        header.setId("order-header");
        header.add(orderId, dateOrdered, customerId, customerName,
                salesRepId, salesRepName, dateShipped, orderFilled, paymentType);

        // Read-only is a runtime state on the mapped component (ADR-0013). The order id
        // is read-only by re-decision: the PK is sequence-assigned, never typed.
        orderId.setReadOnly(true);
        dateOrdered.setReadOnly(true);
        customerId.setReadOnly(true);
        customerName.setReadOnly(true);
        salesRepId.setReadOnly(true);
        salesRepName.setReadOnly(true);
        dateShipped.setReadOnly(true);
        orderFilled.setReadOnly(true);
        paymentType.setReadOnly(true);

        return header;
    }

    private void showOrder(Order order) {
        orderId.setValue(String.valueOf(order.id()));
        dateOrdered.setValue(order.dateOrdered());
        customerId.setValue(String.valueOf(order.customerId()));
        customerName.setValue(order.customerName());
        salesRepId.setValue(order.salesRepId() == null ? "" : String.valueOf(order.salesRepId()));
        salesRepName.setValue(order.salesRepName() == null ? "" : order.salesRepName());
        dateShipped.setValue(order.dateShipped());
        orderFilled.setValue(order.orderFilled());
        paymentType.setValue(order.paymentType());
    }

    private void clearHeader() {
        orderId.clear();
        dateOrdered.clear();
        customerId.clear();
        customerName.clear();
        salesRepId.clear();
        salesRepName.clear();
        dateShipped.clear();
        orderFilled.clear();
        paymentType.clear();
    }
}
