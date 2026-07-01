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
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.FooterRow;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * The ORDERS view — the faithful web rendering of Forms' {@code CV_ORDER} canvas
 * (PRD #38). A clerk quick-searches by order id / customer / sales rep, picks a
 * suggestion, and that single {@code S_ORD} order loads into a read-only header with
 * its {@code S_ITEM} line items coordinated below ({@code S_ORD_S_ITEM}).
 *
 * <p><strong>Layout topology (per {@code migration-rules.yaml}).</strong> The view nests
 * in the AppLayout {@link MainLayout}. The {@code CV_ORDER} Content canvas becomes this
 * top-level {@link VerticalLayout} (rule {@code canvas-content}); the Forms Toolbar
 * canvas becomes a {@link HorizontalLayout} docked at the top (rule
 * {@code canvas-horizontal-toolbar}), here holding the query affordance that replaces
 * Execute/Enter-Query and — from issue #42 — the prev/next pager that replaces Forms'
 * record scrollbar, stepping through the search result set with a "record N of M"
 * indicator. The presenter owns the position; the view only renders it and disables the
 * ends. The non-DB {@code CONTROL} block is a {@code LayoutHost}
 * (rule {@code block-control}): it is <em>not</em> rendered as a container — its items
 * are hoisted into the surrounding layout. Its buttons (stock, image, help, exit,
 * product-LOV) are all out of this slice, so none surface yet.
 *
 * <p><strong>Structural layout fidelity (ADR-0012, {@code graphicsClassifiers}).</strong>
 * Structure is migrated even though visual styling is dropped (ADR-0008):
 * <ul>
 *   <li>The {@code CV_ORDER} boilerplate static text <strong>"Order Information"</strong>
 *       ({@code role: heading}, {@code headingLevel: h2}) renders as an {@link H2}
 *       (rule {@code boilerplate-heading}), heading the header group.</li>
 *   <li>The Forms frame <strong>{@code RECT59}</strong> (which encloses the header
 *       fields) becomes a titled grouping section — a sub-{@link FormLayout} under that
 *       heading (rule {@code frame-group}); <strong>{@code RECT60}</strong> groups the
 *       payment type and the line-item region.</li>
 *   <li>Header fields are placed in <strong>{@code layoutPlan} order</strong> (Y-then-X),
 *       never declaration order: Order Id (outside any frame); then, inside {@code RECT59},
 *       Date Ordered, Customer Id, Customer Name; Sales Rep Id, Sales Rep Name; Date
 *       Shipped, Order Filled; then, inside {@code RECT60}, Payment Type.</li>
 * </ul>
 *
 * <p><strong>Read-only is a state, not a component type (ADR-0013).</strong> Each header
 * field renders as the control the classifier mapped for it, put into a read-only state —
 * Payment Type stays a {@link RadioButtonGroup} (Cash/Credit), Order Filled a
 * {@link Checkbox}, the dates {@link DatePicker}s — never flattened to a
 * {@link TextField}. The later editable slice flips the state, not the component.
 *
 * <p>Visual styling is the Aura theme's (ADR-0008): the frame borders/fills are dropped
 * and re-expressed through the shared {@code .panel} treatment; no Lumo utilities or
 * {@code --lumo-*} properties are used. The view holds no SQL — it talks only to the
 * {@link OrderPresenter} (View &larr; Presenter &larr; Service &larr; Domain &larr; Data).
 */
@Route(value = "orders", layout = MainLayout.class)
@PageTitle("Orders | Summit Migration POC")
public class OrderView extends VerticalLayout {

    /** {@code S_ORD.PAYMENT_TYPE} codes (DB CHECK: CASH/CREDIT) rendered with their Forms radio labels. */
    private static final List<String> PAYMENT_TYPES = List.of("CASH", "CREDIT");

    private final OrderPresenter presenter;

    // Order Id sits outside any Forms frame (layoutPlan section: null) — it is the
    // order's identifier at the top of the canvas, above the RECT59 group.
    private final TextField orderId = new TextField("Order Id");
    // RECT59-enclosed header fields (S_ORD), in layoutPlan (Y-then-X) order.
    private final DatePicker dateOrdered = new DatePicker("Date Ordered");
    private final TextField customerId = new TextField("Customer Id");
    private final TextField customerName = new TextField("Customer Name");
    private final TextField salesRepId = new TextField("Sales Rep Id");
    private final TextField salesRepName = new TextField("Sales Rep Name");
    private final DatePicker dateShipped = new DatePicker("Date Shipped");
    private final Checkbox orderFilled = new Checkbox("Order Filled");
    // RECT60-enclosed header field (S_ORD): payment type, grouped with the line items.
    private final RadioButtonGroup<String> paymentType = new RadioButtonGroup<>("Payment Type");

    private final HorizontalLayout toolbar = new HorizontalLayout();
    private final ComboBox<OrderSummary> search = new ComboBox<>("Find order");
    // Prev/next pager (issue #42) — the record-scrollbar replacement. Position lives in the
    // presenter; these controls render "record N of M" and disable at the result-set ends.
    private final Button previousButton = new Button("Previous");
    private final Button nextButton = new Button("Next");
    private final Span recordIndicator = new Span();
    private final HorizontalLayout pager = new HorizontalLayout();
    private final H2 orderInformationHeading = new H2("Order Information");
    private final FormLayout headerFields = new FormLayout();
    private final Div orderInformationSection = new Div();
    private final Div itemsSection = new Div();
    private final Grid<OrderLine> lineItems = new Grid<>();
    // The order Total footer value (ADR-0001 TOTAL summary) — a loaded order pushes its
    // Domain-computed total (Order.total()) into this cell; the grid never sums.
    private final Span orderTotalValue = new Span();
    private final Paragraph emptyHint = new Paragraph("Search for an order to load its header.");

    // The quick term behind the current suggestion list, captured from the autocomplete's
    // fetch so committing a pick re-runs the same search into the pager's result set.
    private String quickTerm = "";
    // Guards the search field's value-change listener while the pager syncs it to the
    // current record, so stepping does not re-commit the search and reset the position.
    private boolean syncingSelection = false;

    public OrderView(OrderPresenter presenter) {
        this.presenter = presenter;

        buildToolbar();
        buildHeader();
        buildLineItems();
        buildSections();

        // The loaded-order regions stay hidden until a suggestion is picked.
        setOrderVisible(false);
        updatePager();

        add(toolbar, emptyHint, orderId, orderInformationSection, itemsSection);
    }

    /**
     * The Forms Toolbar canvas → {@link HorizontalLayout} docked at the top
     * (rule {@code canvas-horizontal-toolbar}). It holds the Variant A quick search that
     * replaces Forms' Execute/Enter-Query on the single-record {@code S_ORD} master.
     */
    private void buildToolbar() {
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.BASELINE);
        toolbar.setJustifyContentMode(JustifyContentMode.BETWEEN);
        buildSearch();
        buildPager();
        toolbar.add(search, pager);
    }

    private void buildSearch() {
        search.setWidth("420px");
        search.setPlaceholder("Order id, customer, or sales rep");
        search.setItemLabelGenerator(OrderView::suggestionLabel);
        // Server-side autocomplete: each keystroke runs the quick search through the
        // presenter. The fetch honors the ComboBox's lazy window (offset/limit) or
        // Vaadin rejects the result; the search is capped at 50, so the first page holds
        // every suggestion and any later page is empty. The filter is captured so that
        // committing a pick can re-run the same search into the pager's result set.
        search.setItems(query -> {
            quickTerm = query.getFilter().orElse("");
            return presenter.suggest(quickTerm).stream()
                    .skip(query.getOffset())
                    .limit(query.getLimit());
        });
        search.addValueChangeListener(event -> {
            // Ignore value changes the pager itself makes while reflecting the current record.
            if (syncingSelection) {
                return;
            }
            OrderSummary picked = event.getValue();
            if (picked == null) {
                clearHeader();
            } else {
                // Commit the search: the picked term fixes the result set the pager walks,
                // positioned at the picked order (reuses the single-order load path).
                presenter.select(quickTerm, picked.id()).ifPresent(this::showOrder);
            }
            updatePager();
        });
    }

    /**
     * The prev/next pager (issue #42) — Forms' record scrollbar re-expressed as two step
     * controls and a "record N of M" indicator. The position lives in the presenter; each
     * step loads through the same path a pick does, then reflects the landed record back
     * into the search field so it never shows a stale order.
     */
    private void buildPager() {
        previousButton.addClickListener(event -> step(presenter.previous()));
        nextButton.addClickListener(event -> step(presenter.next()));

        pager.setId("order-pager");
        pager.setAlignItems(Alignment.CENTER);
        recordIndicator.setId("order-record-indicator");
        pager.add(previousButton, recordIndicator, nextButton);
    }

    private void step(Optional<Order> loaded) {
        loaded.ifPresent(order -> {
            showOrder(order);
            syncSearchSelection();
        });
        updatePager();
    }

    /** Reflects the current record in the search field without re-committing the search. */
    private void syncSearchSelection() {
        syncingSelection = true;
        try {
            presenter.currentSummary().ifPresent(search::setValue);
        } finally {
            syncingSelection = false;
        }
    }

    /**
     * Renders the presenter's result-set position: the "record N of M" indicator and the
     * end-of-set disabling. The pager is hidden until a search has been committed.
     */
    private void updatePager() {
        int count = presenter.resultCount();
        boolean walkable = count > 0 && presenter.currentRecord() > 0;
        pager.setVisible(walkable);
        if (walkable) {
            recordIndicator.setText("Record " + presenter.currentRecord() + " of " + count);
        }
        previousButton.setEnabled(presenter.hasPrevious());
        nextButton.setEnabled(presenter.hasNext());
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

        // Fields placed in layoutPlan order (Y-then-X), never declaration order (ADR-0012).
        headerFields.add(dateOrdered, customerId, customerName,
                salesRepId, salesRepName,
                dateShipped, orderFilled);
    }

    /**
     * Assembles the two Forms frames into titled grouping sections (ADR-0012,
     * {@code frame-group}). {@code RECT59} → the "Order Information" section (heading +
     * header sub-form); {@code RECT60} → the items section (payment type + line-items
     * grid). The frame border/fill is Aura's to own (ADR-0008) — re-expressed through the
     * shared {@code .panel} treatment, not translated field-by-field.
     */
    private void buildSections() {
        // RECT59 — titled by the CV_ORDER "Order Information" H2 boilerplate heading.
        orderInformationSection.setId("order-information-section");
        orderInformationSection.addClassName("panel");
        headerFields.setId("order-header-fields");
        orderInformationSection.add(orderInformationHeading, headerFields);

        // RECT60 — groups the payment type and the coordinated line-item region. No Forms
        // frame title / boilerplate heading sits on it, so the section carries no heading.
        itemsSection.setId("order-items-section");
        itemsSection.addClassName("panel");
        itemsSection.add(paymentType, lineItems);
    }

    /**
     * The read-only line-items grid. Columns follow the form's order (ADR-0012): Item,
     * Product, Description, Price, Qty, Qty Shipped, Item Total — the {@code S_ITEM}
     * block as the clerk reads it. Read-only is the Grid's default state here (no editor,
     * no Grid Pro); the editable slice adds inline editing later. {@code Item Total} binds
     * to the pure {@link OrderLine#itemTotal()} domain method, not a view-side formula, and
     * the order {@code Total} footer binds to {@link Order#total()} — the Forms {@code TOTAL}
     * summary, computed in the Domain (ADR-0001), never summed in the view.
     */
    private void buildLineItems() {
        lineItems.addColumn(OrderLine::itemId).setHeader("Item").setAutoWidth(true);
        lineItems.addColumn(OrderLine::productId).setHeader("Product").setAutoWidth(true);
        lineItems.addColumn(OrderLine::description).setHeader("Description").setAutoWidth(true).setFlexGrow(1);
        lineItems.addColumn(line -> money(line.price())).setHeader("Price")
                .setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        lineItems.addColumn(OrderLine::quantity).setHeader("Qty")
                .setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        Grid.Column<OrderLine> quantityShippedColumn = lineItems.addColumn(OrderLine::quantityShipped)
                .setHeader("Qty Shipped").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        Grid.Column<OrderLine> itemTotalColumn = lineItems.addColumn(line -> money(line.itemTotal()))
                .setHeader("Item Total").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);

        // The TOTAL summary as a grid footer: a static "Order Total" label over the shipped
        // column, and the Domain-computed amount under the Item Total column. The amount is
        // filled per loaded order in showOrder (Order.total()), never summed here.
        FooterRow footer = lineItems.appendFooterRow();
        footer.getCell(quantityShippedColumn).setText("Order Total");
        orderTotalValue.setId("order-total");
        footer.getCell(itemTotalColumn).setComponent(orderTotalValue);

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
        // The order Total footer binds to the Domain TOTAL summary (ADR-0001), not a view sum.
        orderTotalValue.setText(money(order.total()));

        emptyHint.setVisible(false);
        setOrderVisible(true);
    }

    private void clearHeader() {
        presenter.reset();
        setOrderVisible(false);
        lineItems.setItems(List.of());
        emptyHint.setVisible(true);
    }

    /** Shows or hides the loaded-order regions (Order Id + both frame sections) together. */
    private void setOrderVisible(boolean visible) {
        orderId.setVisible(visible);
        orderInformationSection.setVisible(visible);
        itemsSection.setVisible(visible);
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
