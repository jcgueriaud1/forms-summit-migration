package com.example.summit.ui;

import com.example.summit.domain.Order;
import com.example.summit.domain.OrderLine;
import com.example.summit.domain.OrderSummary;
import com.example.summit.presenter.OrderPresenter;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.math.BigDecimal;
import java.util.List;

/**
 * The ORDERS view — the first real form migration (PRD #38, Step 1a). A clerk quick-
 * searches by order id / customer / sales rep, picks a suggestion, and that single
 * {@code S_ORD} order loads into a <strong>read-only header {@link FormLayout}</strong>.
 *
 * <p>This is the faithful web rendering of Forms' single-record Enter-Query on the
 * {@code S_ORD} master (Variant A, {@code block-master-single}): a lookup that loads
 * one record, not a grid of orders. Below the header, the order's {@code S_ITEM} line
 * items load automatically into a <strong>read-only {@link Grid}</strong> coordinated on
 * the same order ({@code S_ORD_S_ITEM}, #40) — picking a different order reloads header
 * and lines together. The layout shell, pager, and advanced search (#41), editing,
 * totals, and commit all arrive in later slices. The view holds no SQL — it talks only
 * to the {@link OrderPresenter} (View &larr; Presenter &larr; Service &larr; Domain
 * &larr; Data).
 *
 * <p><strong>Read-only is a state, not a component type (ADR-0013).</strong> Each
 * header field renders as the control the classifier mapped for it, put into a
 * read-only state — Payment Type stays a {@link RadioButtonGroup} (Cash/Credit),
 * Order Filled a {@link Checkbox}, the dates {@link DatePicker}s — never flattened to
 * a {@link TextField}. The later editable slice flips the state, not the component.
 */
@Route(value = "orders", layout = MainLayout.class)
@PageTitle("Orders | Summit Migration POC")
public class OrderView extends VerticalLayout {

    /** {@code S_ORD.PAYMENT_TYPE} codes (DB CHECK: CASH/CREDIT) rendered with their Forms radio labels. */
    private static final List<String> PAYMENT_TYPES = List.of("CASH", "CREDIT");

    private final OrderPresenter presenter;

    private final TextField orderId = new TextField("Order Id");
    private final TextField customerId = new TextField("Customer Id");
    private final TextField customerName = new TextField("Customer Name");
    private final DatePicker dateOrdered = new DatePicker("Date Ordered");
    private final DatePicker dateShipped = new DatePicker("Date Shipped");
    private final TextField salesRepId = new TextField("Sales Rep Id");
    private final TextField salesRepName = new TextField("Sales Rep Name");
    private final RadioButtonGroup<String> paymentType = new RadioButtonGroup<>("Payment Type");
    private final Checkbox orderFilled = new Checkbox("Order Filled");

    private final FormLayout header = new FormLayout();
    private final Grid<OrderLine> lineItems = new Grid<>();
    private final Paragraph emptyHint = new Paragraph("Search for an order to load its header.");

    public OrderView(OrderPresenter presenter) {
        this.presenter = presenter;

        buildHeader();
        buildLineItems();
        header.setVisible(false);
        lineItems.setVisible(false);
        add(buildSearch(), emptyHint, header, lineItems);
    }

    private ComboBox<OrderSummary> buildSearch() {
        ComboBox<OrderSummary> search = new ComboBox<>("Find order");
        search.setWidth("420px");
        search.setPlaceholder("Order id, customer, or sales rep");
        search.setItemLabelGenerator(OrderView::suggestionLabel);
        // Server-side autocomplete: each keystroke runs the quick search through the
        // presenter. The fetch honors the ComboBox's lazy window (offset/limit) or
        // Vaadin rejects the result; the search is capped at 50, so the first page holds
        // every suggestion and any later page is empty.
        search.setItems(query -> presenter.suggest(query.getFilter().orElse("")).stream()
                .skip(query.getOffset())
                .limit(query.getLimit()));
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

    private static String suggestionLabel(OrderSummary summary) {
        String rep = summary.salesRepName() == null ? "no rep" : summary.salesRepName();
        return "#" + summary.id() + " — " + summary.customerName() + " (" + rep + ")";
    }

    private void buildHeader() {
        // Payment Type is a Forms Radio Group (radio-group classifier) — a small fixed
        // choice — so it stays a RadioButtonGroup even read-only (ADR-0013), showing the
        // Forms labels "Cash"/"Credit" over the S_ORD.PAYMENT_TYPE codes.
        paymentType.setItems(PAYMENT_TYPES);
        paymentType.setItemLabelGenerator(OrderView::paymentTypeLabel);

        // Read-only is a runtime state on the mapped control, not a component swap.
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

    /**
     * The read-only line-items grid. Columns follow the form's order (ADR-0012): Item,
     * Product, Description, Price, Qty, Qty Shipped, Item Total — the {@code S_ITEM}
     * block as the clerk reads it. Read-only is the Grid's default state here (no editor,
     * no Grid Pro); the editable slice adds inline editing later. {@code Item Total} binds
     * to the pure {@link OrderLine#itemTotal()} domain method, not a view-side formula.
     */
    private void buildLineItems() {
        lineItems.addColumn(OrderLine::itemId).setHeader("Item").setAutoWidth(true);
        lineItems.addColumn(OrderLine::productId).setHeader("Product").setAutoWidth(true);
        lineItems.addColumn(OrderLine::description).setHeader("Description").setAutoWidth(true).setFlexGrow(1);
        lineItems.addColumn(line -> money(line.price())).setHeader("Price")
                .setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        lineItems.addColumn(OrderLine::quantity).setHeader("Qty")
                .setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        lineItems.addColumn(OrderLine::quantityShipped).setHeader("Qty Shipped")
                .setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        lineItems.addColumn(line -> money(line.itemTotal())).setHeader("Item Total")
                .setTextAlign(ColumnTextAlign.END).setAutoWidth(true);

        // A short, header-coordinated detail block — size to its rows rather than scroll
        // inside a fixed viewport, so the whole order reads at a glance.
        lineItems.setAllRowsVisible(true);
        lineItems.setWidthFull();
    }

    /** Renders a money amount with two decimals; a null amount shows blank. */
    private static String money(BigDecimal amount) {
        return amount == null ? "" : String.format("%.2f", amount);
    }

    private static String paymentTypeLabel(String code) {
        return switch (code) {
            case "CASH" -> "Cash";
            case "CREDIT" -> "Credit";
            default -> code;
        };
    }

    private void showOrder(Order order) {
        orderId.setValue(String.valueOf(order.id()));
        customerId.setValue(String.valueOf(order.customerId()));
        customerName.setValue(nullSafe(order.customerName()));
        dateOrdered.setValue(order.dateOrdered());
        dateShipped.setValue(order.dateShipped());
        salesRepId.setValue(order.salesRepId() == null ? "" : String.valueOf(order.salesRepId()));
        salesRepName.setValue(nullSafe(order.salesRepName()));
        paymentType.setValue(order.paymentType());
        orderFilled.setValue(order.orderFilled());

        // S_ORD_S_ITEM coordination: the grid always shows the loaded order's lines, so
        // switching orders replaces the contents outright.
        lineItems.setItems(order.lines());

        emptyHint.setVisible(false);
        header.setVisible(true);
        lineItems.setVisible(true);
    }

    private void clearHeader() {
        header.setVisible(false);
        lineItems.setVisible(false);
        lineItems.setItems(List.of());
        emptyHint.setVisible(true);
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
