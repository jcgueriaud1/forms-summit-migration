package com.example.summit.ui;

import com.example.summit.domain.Order;
import com.example.summit.domain.OrderLine;
import com.example.summit.domain.OrderSummary;
import com.example.summit.presenter.OrderPresenter;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * The ORDERS view — the faithful web rendering of Summit's {@code ORDERS} Forms window.
 * A clerk quick-searches by order id / customer / sales rep, picks a suggestion, and that
 * one {@code S_ORD} order loads into a <strong>read-only header {@link FormLayout}</strong>
 * with its {@code S_ITEM} line items below.
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
 * it is <em>not</em> rendered as a block. Its toolbar-docked push buttons are hoisted straight
 * into the toolbar {@link HorizontalLayout}. Their target behaviours (the {@code S_INVENTORY}
 * stock view, the product image, the help canvas, exit/menu wiring) are out of scope for this
 * PRD, so the buttons are present but deferred — each says where its behaviour lands rather than
 * faking it.
 *
 * <h2>Search UX</h2>
 * Variant A ({@code block-master-single}): Forms' single-record Enter-Query becomes a lookup
 * that loads one record, not a grid of orders. The read-only line-items {@link Grid} renders the
 * {@code S_ORD_S_ITEM} master-detail; header and detail load together and stay coordinated.
 * The advanced-search panel (#14) and the prev/next pager (#13) layer on in their own slices.
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
    private final TextField paymentType = new TextField("Payment Type");
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
        // Hoisted CONTROL buttons. Their behaviours are deferred to later slices / a follow-up
        // PRD (see this class's Javadoc and prd-orders-view.md "Out of Scope"); they are rendered
        // to make the dissolved-CONTROL topology visible, not to fake the deferred behaviour.
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
     * the Variant A lookup, the read-only {@code S_ORD} header {@link FormLayout}, and the
     * {@code S_ITEM} line-items {@link Grid}.
     */
    private VerticalLayout buildContent() {
        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.add(buildSearch(), emptyHint, header, lineItems);
        return content;
    }

    private ComboBox<OrderSummary> buildSearch() {
        ComboBox<OrderSummary> search = new ComboBox<>("Find order");
        search.setWidth("420px");
        search.setPlaceholder("Order id, customer, or sales rep");
        search.setItemLabelGenerator(OrderView::suggestionLabel);
        // Server-side autocomplete: each keystroke runs the quick search through the presenter.
        // The fetch must honor the ComboBox's lazy-loading window (offset/limit) or Vaadin
        // rejects the result; the quick search is already capped at 50, so the first page
        // holds every suggestion and later pages are empty.
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

    /**
     * The read-only detail grid over the order's {@code S_ITEM} lines. A plain {@link Grid}
     * with explicit columns (no editor) — read-only by construction, matching the Step 1b
     * scope. {@code Description} is the joined product name and {@code Item Total} is the
     * domain {@link OrderLine#itemTotal()} formula ({@code quantity_shipped * price}).
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
        paymentType.setValue(nullSafe(order.paymentType()));
        orderFilled.setValue(order.orderFilled());

        // S_ORD_S_ITEM coordination: the detail grid is reloaded with the picked order's
        // lines, so switching orders replaces the contents (header and detail stay in sync).
        lineItems.setItems(order.lines());

        emptyHint.setVisible(false);
        header.setVisible(true);
        lineItems.setVisible(true);
    }

    private void clearHeader() {
        header.setVisible(false);
        lineItems.setVisible(false);
        lineItems.setItems(java.util.Collections.emptyList());
        emptyHint.setVisible(true);
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
