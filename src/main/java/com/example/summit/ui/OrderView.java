package com.example.summit.ui;

import com.example.summit.domain.Order;
import com.example.summit.domain.OrderLine;
import com.example.summit.domain.OrderSummary;
import com.example.summit.presenter.OrderPresenter;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * The ORDERS view — the faithful web rendering of Summit's {@code ORDERS} Forms window.
 * A clerk quick-searches by order id / customer / sales rep, picks a suggestion, and that
 * one {@code S_ORD} order loads into a <strong>read-only header</strong> with its
 * {@code S_ITEM} line items below.
 *
 * <h2>Layout topology (PRD #8, Step 1c — {@code migration-rules.yaml})</h2>
 * The view nests in the {@link MainLayout} {@code AppLayout} shell and lays the form's two
 * in-scope canvases out as the ruleset prescribes:
 * <ul>
 *   <li><b>{@code TOOLBAR}</b> (Horizontal Toolbar canvas) &rarr; a {@link HorizontalLayout}
 *       docked at the top ({@code canvas-horizontal-toolbar}).</li>
 *   <li><b>{@code CV_ORDER}</b> (Content canvas) &rarr; a {@link VerticalLayout} holding the
 *       search, header and line items ({@code canvas-content}).</li>
 * </ul>
 * The non-database <b>{@code CONTROL}</b> block is a {@code LayoutHost} ({@code block-control}):
 * its toolbar-docked push buttons are hoisted straight into the toolbar.
 *
 * <h2>Layout fidelity (ADR-0012 / ADR-0013)</h2>
 * The header reproduces the Forms screen <em>structurally</em>, from the analyzer's layout
 * signals — not an invented order:
 * <ul>
 *   <li><b>Field order</b> follows the {@code S_ORD} {@code layoutPlan} (Y-then-X): Order Id,
 *       then the {@code Date Ordered / Customer Id / Customer Name} row, the
 *       {@code Sales Rep Id / Sales Rep Name} row, the {@code Date Shipped / Order Filled} row,
 *       then Payment Type — the order the clerk reads them in.</li>
 *   <li><b>Grouping + title</b>: the Forms frame ({@code RECT59}) that boxes the header becomes
 *       a titled section ({@code frame-group}), headed by the boilerplate text
 *       <em>"Order Information"</em> ({@code boilerplate-heading} &rarr; {@link H2}).</li>
 *   <li><b>Component type</b>: {@code PAYMENT_TYPE} is a Forms Radio Group, so it renders as a
 *       {@link RadioButtonGroup} ({@code Cash} / {@code Credit}) — read-only here, not collapsed
 *       to a text field. Read-only is a <em>state</em>, not a type (ADR-0013).</li>
 * </ul>
 *
 * <p>The view holds no SQL — it talks only to the {@link OrderPresenter}
 * (View &larr; Presenter &larr; Service &larr; Data).
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
    private final RadioButtonGroup<String> paymentType = new RadioButtonGroup<>("Payment Type");
    private final Checkbox orderFilled = new Checkbox("Order Filled");

    private final H2 orderInfoHeading = new H2("Order Information");
    private final VerticalLayout headerSection = new VerticalLayout();
    private final Grid<OrderLine> lineItems = new Grid<>();
    private final Paragraph emptyHint = new Paragraph("Search for an order to load its header.");

    public OrderView(OrderPresenter presenter) {
        this.presenter = presenter;

        buildHeader();
        buildLineItems();
        headerSection.setVisible(false);
        lineItems.setVisible(false);

        setSizeFull();
        add(buildToolbar(), buildContent());
    }

    /**
     * The {@code TOOLBAR} canvas (Horizontal Toolbar &rarr; {@link HorizontalLayout}), docked at
     * the top of the view. The {@code CONTROL} block dissolves here ({@code block-control}
     * &rarr; {@code LayoutHost}): its toolbar-docked push buttons are hoisted directly into this
     * row rather than wrapped in any block container.
     */
    private HorizontalLayout buildToolbar() {
        HorizontalLayout toolbar = new HorizontalLayout();
        toolbar.setWidthFull();
        toolbar.setPadding(false);
        toolbar.add(
                deferredButton("Stock",
                        "Stock view for the current line's product (S_INVENTORY) — deferred to its own follow-up PRD."),
                deferredButton("Image On",
                        "Product image (filesystem TIFF, default-hidden) — deferred; needs TIFF→PNG/JPEG conversion."),
                deferredButton("Help",
                        "Help canvas (CV_HELP) — out of scope for this PRD."),
                deferredButton("Exit",
                        "Exit / menu wiring — out of scope for this PRD."));
        return toolbar;
    }

    /**
     * A hoisted {@code CONTROL} toolbar button whose Forms behaviour is out of scope for this PRD.
     * The button keeps its Forms label and explains, on click and via its tooltip, where the
     * behaviour lands — an honest deferral rather than a dead control.
     */
    private Button deferredButton(String label, String deferredReason) {
        Button button = new Button(label);
        button.setTooltipText(deferredReason);
        button.addClickListener(event -> Notification.show(label + " — not part of this slice. " + deferredReason));
        return button;
    }

    /**
     * The {@code CV_ORDER} Content canvas ({@code canvas-content} &rarr; {@link VerticalLayout}):
     * the Variant A lookup, the read-only {@code S_ORD} header section, and the {@code S_ITEM}
     * line-items {@link Grid}.
     */
    private VerticalLayout buildContent() {
        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.add(buildSearch(), emptyHint, headerSection, lineItems);
        return content;
    }

    private ComboBox<OrderSummary> buildSearch() {
        ComboBox<OrderSummary> search = new ComboBox<>("Find order");
        search.setWidth("420px");
        search.setPlaceholder("Order id, customer, or sales rep");
        search.setItemLabelGenerator(OrderView::suggestionLabel);
        // Server-side autocomplete: each keystroke runs the quick search through the presenter.
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

    private static String suggestionLabel(OrderSummary s) {
        String rep = s.salesRepName() == null ? "no rep" : s.salesRepName();
        return "#" + s.id() + " — " + s.customerName() + " (" + rep + ")";
    }

    /**
     * Builds the read-only "Order Information" section — the Forms frame ({@code RECT59})
     * rendered as a titled grouping section ({@code frame-group}, ADR-0012). The header fields
     * are placed in the {@code S_ORD} {@code layoutPlan} order and grouped into the rows the
     * clerk reads them in. {@code PAYMENT_TYPE} stays a {@link RadioButtonGroup} (its Forms
     * type), read-only via state, not flattened to a text field (ADR-0013).
     */
    private void buildHeader() {
        orderId.setReadOnly(true);
        customerId.setReadOnly(true);
        customerName.setReadOnly(true);
        dateOrdered.setReadOnly(true);
        dateShipped.setReadOnly(true);
        salesRepId.setReadOnly(true);
        salesRepName.setReadOnly(true);
        orderFilled.setReadOnly(true);

        // Forms Radio Group → RadioButtonGroup (radio-group rule); options Cash=CASH, Credit=CREDIT.
        paymentType.setItems("CASH", "CREDIT");
        paymentType.setItemLabelGenerator(value -> "CASH".equals(value) ? "Cash" : "Credit");
        paymentType.setReadOnly(true);

        headerSection.setId("order-information");
        headerSection.setPadding(false);
        headerSection.setSpacing(false);
        headerSection.add(
                orderInfoHeading,
                fieldRow(orderId),
                fieldRow(dateOrdered, customerId, customerName),
                fieldRow(salesRepId, salesRepName),
                fieldRow(dateShipped, orderFilled),
                fieldRow(paymentType));
    }

    /** One {@code layoutPlan} row (Forms reads fields left→right within a row). */
    private HorizontalLayout fieldRow(Component... fields) {
        HorizontalLayout row = new HorizontalLayout(fields);
        row.setWidthFull();
        return row;
    }

    /**
     * The read-only detail grid over the order's {@code S_ITEM} lines. {@code Description} is the
     * joined product name and {@code Item Total} is the domain {@link OrderLine#itemTotal()}
     * formula ({@code quantity_shipped * price}).
     */
    private void buildLineItems() {
        lineItems.addColumn(OrderLine::itemId).setHeader("Item").setAutoWidth(true);
        lineItems.addColumn(OrderLine::productId).setHeader("Product").setAutoWidth(true);
        lineItems.addColumn(OrderLine::productName).setHeader("Description").setFlexGrow(1);
        lineItems.addColumn(OrderLine::price).setHeader("Price").setAutoWidth(true);
        lineItems.addColumn(OrderLine::quantity).setHeader("Qty").setAutoWidth(true);
        lineItems.addColumn(OrderLine::quantityShipped).setHeader("Qty Shipped").setAutoWidth(true);
        lineItems.addColumn(OrderLine::itemTotal).setHeader("Item Total").setAutoWidth(true);
        lineItems.setAllRowsVisible(true);
    }

    private void showOrder(Order order) {
        orderId.setValue(String.valueOf(order.id()));
        customerId.setValue(String.valueOf(order.customerId()));
        customerName.setValue(nullSafe(order.customerName()));
        dateOrdered.setValue(order.dateOrdered());
        dateShipped.setValue(order.dateShipped());
        salesRepId.setValue(order.salesRepId() == null ? "" : String.valueOf(order.salesRepId()));
        salesRepName.setValue(nullSafe(order.salesRepName()));
        String pt = order.paymentType();
        if ("CASH".equals(pt) || "CREDIT".equals(pt)) {
            paymentType.setValue(pt);
        } else {
            paymentType.clear();
        }
        orderFilled.setValue(order.orderFilled());

        // S_ORD_S_ITEM coordination: the detail grid is reloaded with the picked order's lines.
        lineItems.setItems(order.lines());

        emptyHint.setVisible(false);
        headerSection.setVisible(true);
        lineItems.setVisible(true);
    }

    private void clearHeader() {
        headerSection.setVisible(false);
        lineItems.setVisible(false);
        lineItems.setItems(java.util.Collections.emptyList());
        emptyHint.setVisible(true);
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
