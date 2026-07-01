package com.example.summit.ui;

import com.example.summit.domain.CommitResult;
import com.example.summit.domain.Customer;
import com.example.summit.domain.Order;
import com.example.summit.domain.OrderSearchCriteria;
import com.example.summit.domain.OrderSummary;
import com.example.summit.domain.Product;
import com.example.summit.presenter.EditableHeader;
import com.example.summit.presenter.EditableLine;
import com.example.summit.presenter.OrderPresenter;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.editor.Editor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The ORDERS view — the faithful web rendering of Summit's {@code ORDERS} Forms window.
 * A clerk quick-searches by order id / customer / sales rep, picks a suggestion, and that
 * one {@code S_ORD} order loads into an <strong>editable header</strong> with its
 * {@code S_ITEM} line items below. The header folds in the {@code S_ORD} Forms validation
 * behaviours (issue #18): the customer-id lookup ({@code WHEN-VALIDATE-ITEM}), the credit
 * guard ({@code WHEN-RADIO-CHANGED}) and the ship-date guard ({@code WHEN-VALIDATE-RECORD}).
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
 *       {@link RadioButtonGroup} ({@code Cash} / {@code Credit}), not collapsed to a text field —
 *       the component type is preserved whether the field is read-only or editable (ADR-0013).</li>
 * </ul>
 *
 * <p>The view holds no SQL — it talks only to the {@link OrderPresenter}
 * (View &larr; Presenter &larr; Service &larr; Data).
 */
@Route(value = "orders", layout = MainLayout.class)
@PageTitle("Orders | Summit Migration POC")
public class OrderView extends VerticalLayout {

    private static final String NO_ORDER_HINT = "Search for an order to load its header.";
    private static final String NO_MATCH_HINT = "No orders match your search.";

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
    // The editable header is bound to the working header bean, unbuffered (ADR-0002, no Signals):
    // the Binder writes the clerk's edits straight in and runs the ship-date validator live.
    private final Binder<EditableHeader> headerBinder = new Binder<>(EditableHeader.class);
    // Guards the header listeners while an order is loaded into the form: the customer-id defaulter
    // and the credit guard must fire only on a clerk's edit, not when setBean / setValue seed the
    // fields from the loaded order (Forms fires WHEN-VALIDATE-* on validation, not on query).
    private boolean suppressHeaderListeners;
    private final Grid<EditableLine> lineItems = new Grid<>();
    private Grid.Column<EditableLine> descriptionColumn;
    private Grid.Column<EditableLine> itemTotalColumn;
    // Read-only display of the edited row's ITEM_TOTAL, shown while its editor is open so the row
    // total recomputes live without re-rendering (and closing) the editor.
    private final Span itemTotalEditor = new Span();
    // Read-only display of the edited row's product description (S_PRODUCT.name), shown while the
    // editor is open so the WHEN-VALIDATE-ITEM re-default shows live, again without refreshing (and
    // closing) the open editor (issue #17, see vaadin-grid-editor-live-recompute).
    private final Span descriptionEditor = new Span();
    // Guards the product ComboBox value-change listener while the editor seeds it from the line on
    // open: the WHEN-VALIDATE-ITEM defaulter must fire only on a clerk's product change, not when an
    // existing line is loaded into the editor (Forms fires it on validation, not on query).
    private boolean suppressProductDefaulter;
    private final Paragraph emptyHint = new Paragraph(NO_ORDER_HINT);

    // The detail-block editing controls (issue #16). Add appends a line with the next per-order
    // item id and opens its editor; Remove deletes the selected line. They sit under the grid and
    // share its visibility.
    private final Button addLine = new Button("Add line");
    private final Button removeLine = new Button("Remove line");
    private final HorizontalLayout lineActions = new HorizontalLayout(addLine, removeLine);

    // The KEY-COMMIT action (COMMIT_FORM → transaction-commit-form, issue #19): one Commit persists
    // the header + all line items as one transaction. Enabled only while an order is loaded; also
    // bound to Ctrl+S (the keystroke Forms' KEY-COMMIT trapped, per trigger-key-commit).
    private final Button commit = new Button("Commit");

    // Quick search (autocomplete picker) — by order id / customer / sales rep.
    private final ComboBox<OrderSummary> quickSearch = new ComboBox<>("Find order");

    // Advanced search (the QBE replacement): each field AND-narrows the quick term. A blank
    // field is "not set" and contributes no predicate; Clear resets every field at once.
    private final TextField customerFilter = new TextField("Customer");
    private final TextField salesRepFilter = new TextField("Sales Rep");
    private final ComboBox<String> paymentTypeFilter = new ComboBox<>("Payment Type");
    private final DatePicker orderedFromFilter = new DatePicker("Ordered from");
    private final DatePicker orderedToFilter = new DatePicker("Ordered to");
    private final ComboBox<String> statusFilter = new ComboBox<>("Status");
    private final Button searchButton = new Button("Search");
    private final Button clearButton = new Button("Clear");

    // The replacement for Forms' record scrollbar: prev/next step through the result set, with a
    // "record N of M" indicator. The position lives in the presenter (issue #13); this is its view.
    private final Button prevRecord = new Button("‹ Previous");
    private final Button nextRecord = new Button("Next ›");
    private final Span recordIndicator = new Span();
    private final HorizontalLayout pager = new HorizontalLayout(prevRecord, recordIndicator, nextRecord);

    /** The quick term that produced the current suggestions — the result set prev/next steps. */
    private String lastSearchTerm = "";

    public OrderView(OrderPresenter presenter) {
        this.presenter = presenter;

        buildHeader();
        buildLineItems();
        buildPager();
        headerSection.setVisible(false);
        lineItems.setVisible(false);
        lineActions.setVisible(false);
        pager.setVisible(false);

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

        // The real KEY-COMMIT action (unlike the deferred CONTROL buttons below): one Commit runs
        // the whole-graph @Transactional save. Disabled until an order is loaded; Ctrl+S triggers it.
        commit.setId("order-commit");
        commit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        commit.setEnabled(false);
        commit.addClickListener(event -> onCommit());
        commit.addClickShortcut(Key.KEY_S, KeyModifier.CONTROL);

        toolbar.add(
                commit,
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
        content.add(buildSearch(), buildAdvancedPanel(), pager, emptyHint, headerSection, lineItems, lineActions);
        return content;
    }

    /**
     * The prev/next pager — the faithful web rendering of Forms' record scrollbar (issue #13).
     * It owns no position: it reads {@link OrderPresenter#hasPrevious()}/{@code hasNext()} and the
     * "record N of M" counts back from the presenter, and asks it to step. Prev is disabled at the
     * first match, next at the last.
     */
    private void buildPager() {
        prevRecord.addClickListener(event -> presenter.previous().ifPresent(this::showOrder));
        nextRecord.addClickListener(event -> presenter.next().ifPresent(this::showOrder));
        pager.setPadding(false);
        recordIndicator.getStyle().set("align-self", "center");
    }

    /** Reflects the presenter's cursor in the pager — the single place the indicator/buttons sync. */
    private void refreshPager() {
        boolean hasResultSet = presenter.hasResultSet();
        pager.setVisible(hasResultSet);
        if (hasResultSet) {
            recordIndicator.setText("Record " + presenter.recordNumber() + " of " + presenter.recordCount());
            prevRecord.setEnabled(presenter.hasPrevious());
            nextRecord.setEnabled(presenter.hasNext());
        }
    }

    private ComboBox<OrderSummary> buildSearch() {
        quickSearch.setWidth("420px");
        quickSearch.setPlaceholder("Order id, customer, or sales rep");
        quickSearch.setItemLabelGenerator(OrderView::suggestionLabel);
        // Server-side autocomplete: each keystroke runs the search through the presenter, with
        // the advanced filters AND-ed in, so the suggestions already reflect the narrowed set.
        // We remember the typed term so picking a suggestion opens that same combined result set.
        quickSearch.setItems(query -> {
            lastSearchTerm = query.getFilter().orElse("");
            return presenter.suggest(currentCriteria()).stream()
                    .skip(query.getOffset())
                    .limit(query.getLimit());
        });
        quickSearch.addValueChangeListener(event -> {
            OrderSummary picked = event.getValue();
            if (picked == null) {
                clearHeader();
            } else {
                presenter.open(currentCriteria(), picked).ifPresent(this::showOrder);
            }
        });
        return quickSearch;
    }

    /**
     * The advanced search panel — the QBE (Enter-Query) replacement (issue #14). A collapsible
     * {@link Details} exposes the multi-field filters; each one combines with the quick term and
     * with the others by <strong>AND</strong>, so a clerk can narrow precisely. <em>Search</em>
     * runs the combined criteria and loads the result set positioned at its first record (the
     * pager then reflects the search exactly — one match is "record 1 of 1"); <em>Clear</em>
     * resets every criterion, quick and advanced, for a fresh lookup.
     */
    private Details buildAdvancedPanel() {
        // "Any" is the unset state for the fixed-choice filters (cleared → no predicate).
        paymentTypeFilter.setItems("CASH", "CREDIT");
        paymentTypeFilter.setItemLabelGenerator(value -> "CASH".equals(value) ? "Cash" : "Credit");
        paymentTypeFilter.setPlaceholder("Any");
        statusFilter.setItems("Filled", "Not filled");
        statusFilter.setPlaceholder("Any");

        FormLayout filters = new FormLayout(
                customerFilter, salesRepFilter, paymentTypeFilter,
                orderedFromFilter, orderedToFilter, statusFilter);

        searchButton.setId("order-search");
        searchButton.addClickListener(event ->
                presenter.search(currentCriteria()).ifPresentOrElse(this::showOrder, this::showNoMatches));
        clearButton.setId("order-clear");
        clearButton.addClickListener(event -> clearSearch());
        HorizontalLayout actions = new HorizontalLayout(searchButton, clearButton);

        VerticalLayout body = new VerticalLayout(filters, actions);
        body.setPadding(false);
        Details panel = new Details("Advanced search", body);
        panel.setId("advanced-search");
        return panel;
    }

    /**
     * The clerk's current search as a single immutable criteria — the quick term last typed into
     * {@link #quickSearch} AND every advanced filter that is set. A cleared advanced field is
     * passed as {@code null}/blank, which the value object normalises away to "no predicate".
     */
    private OrderSearchCriteria currentCriteria() {
        Boolean orderFilled = statusFilter.getValue() == null
                ? null
                : "Filled".equals(statusFilter.getValue());
        return new OrderSearchCriteria(
                lastSearchTerm,
                customerFilter.getValue(),
                salesRepFilter.getValue(),
                paymentTypeFilter.getValue(),
                orderedFromFilter.getValue(),
                orderedToFilter.getValue(),
                orderFilled);
    }

    private static String suggestionLabel(OrderSummary s) {
        String rep = s.salesRepName() == null ? "no rep" : s.salesRepName();
        return "#" + s.id() + " — " + s.customerName() + " (" + rep + ")";
    }

    /**
     * Builds the editable "Order Information" section — the Forms frame ({@code RECT59})
     * rendered as a titled grouping section ({@code frame-group}, ADR-0012). The header fields
     * are placed in the {@code S_ORD} {@code layoutPlan} order and grouped into the rows the
     * clerk reads them in. {@code PAYMENT_TYPE} stays a {@link RadioButtonGroup} (its Forms
     * type), not flattened to a text field (ADR-0013). The editable fields fold in the
     * {@code S_ORD} Forms validation behaviours through {@link #bindHeader()} (issue #18).
     */
    private void buildHeader() {
        // Order id is the PK (display only); customer name and the sales-rep id/name are
        // lookup-defaulted outputs the clerk never types — read-only, re-defaulted from the
        // customer master when CUSTOMER_ID changes (WHEN-VALIDATE-ITEM). Everything else is now
        // editable (issue #18): customer id, both dates, payment type and order-filled.
        orderId.setReadOnly(true);
        customerName.setReadOnly(true);
        salesRepId.setReadOnly(true);
        salesRepName.setReadOnly(true);
        customerId.setId("order-customer-id");

        // Forms Radio Group → RadioButtonGroup (radio-group rule); options Cash=CASH, Credit=CREDIT.
        paymentType.setItems("CASH", "CREDIT");
        paymentType.setItemLabelGenerator(value -> "CASH".equals(value) ? "Cash" : "Credit");

        bindHeader();

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
     * Binds the editable header fields to the working header bean and folds in the {@code S_ORD}
     * Forms validation behaviours (issue #18). Binding is unbuffered (ADR-0002, no Signals): each
     * edit writes straight into the bean. The customer-id defaulter and the credit guard are
     * value-change listeners (the {@code WHEN-VALIDATE-ITEM} / {@code WHEN-RADIO-CHANGED} idioms);
     * the ship-date guard is a Binder field validator delegating to the authoritative domain check.
     */
    private void bindHeader() {
        headerBinder.forField(dateOrdered)
                .bind(EditableHeader::getDateOrdered, EditableHeader::setDateOrdered);
        // The S_ORD WHEN-VALIDATE-RECORD ship-date guard, mirrored to a Binder validator that
        // delegates to the authoritative domain check (Order.shipDateValid, ADR-0001/0002). The web
        // model flags rather than focus-traps: an earlier ship date stays on screen, invalid, with
        // its message, until the clerk fixes it (a null or equal date is allowed).
        headerBinder.forField(dateShipped)
                .withValidator(shipped -> Order.shipDateValid(dateOrdered.getValue(), shipped),
                        "Ship date cannot be before the order date.")
                .bind(EditableHeader::getDateShipped, EditableHeader::setDateShipped);
        // The ship-date guard is cross-field, so a change to the order date re-checks the ship date.
        dateOrdered.addValueChangeListener(event -> headerBinder.validate());

        headerBinder.forField(paymentType)
                .bind(EditableHeader::getPaymentType, EditableHeader::setPaymentType);
        headerBinder.forField(orderFilled)
                .bind(EditableHeader::isOrderFilled, EditableHeader::setOrderFilled);

        // The WHEN-VALIDATE-ITEM customer-id defaulter: typing a customer id resolves it against the
        // customer master and re-defaults the name + sales rep; an unknown id is the NO_DATA_FOUND
        // "Invalid Customer Id!" reject. customerId is not bound (it is the lookup's source, not a
        // plain field) — the listener writes the resolved id into the working header.
        customerId.addValueChangeListener(event -> {
            if (suppressHeaderListeners) {
                return;
            }
            onCustomerIdEntered(event.getValue());
        });

        // The WHEN-RADIO-CHANGED credit guard: a CREDIT selection the customer is not eligible for
        // (credit rating not GOOD/EXCELLENT) is refused and reset to CASH with a notice — the web
        // counterpart of the Forms Payment_Type_Alert.
        paymentType.addValueChangeListener(event -> {
            if (suppressHeaderListeners) {
                return;
            }
            if ("CREDIT".equals(event.getValue()) && !presenter.paymentAllowed("CREDIT")) {
                suppressHeaderListeners = true;
                paymentType.setValue("CASH");
                suppressHeaderListeners = false;
                Notification.show("Credit is not available for this customer — payment reset to Cash.");
            }
        });
    }

    /**
     * The customer-id defaulter's body: resolves the typed id through the presenter and either fills
     * the name + sales-rep display fields from the customer master, or rejects an unknown / non-numeric
     * id with the Forms <em>"Invalid Customer Id!"</em> message, clearing the dependent display.
     */
    private void onCustomerIdEntered(String raw) {
        Long id = parseId(raw);
        Optional<Customer> resolved = id == null ? Optional.empty() : presenter.applyCustomerDefaults(id);
        if (resolved.isPresent()) {
            Customer customer = resolved.get();
            customerId.setInvalid(false);
            customerId.setErrorMessage(null);
            customerName.setValue(nullSafe(customer.name()));
            salesRepId.setValue(customer.salesRepId() == null ? "" : String.valueOf(customer.salesRepId()));
            salesRepName.setValue(nullSafe(customer.salesRepName()));
        } else {
            // Forms NO_DATA_FOUND → "Invalid Customer Id!" (RAISE FORM_TRIGGER_FAILURE). The web model
            // flags rather than focus-traps: the field stays invalid with the message and the
            // dependent display is cleared so a stale name/rep cannot linger beside a bad id.
            customerId.setInvalid(true);
            customerId.setErrorMessage("Invalid Customer Id!");
            customerName.clear();
            salesRepId.clear();
            salesRepName.clear();
            Notification.show("Invalid Customer Id!");
        }
    }

    /**
     * The editable detail grid over the order's {@code S_ITEM} lines (issue #16). {@code Description}
     * is the joined product name and {@code Item Total} is the domain {@link EditableLine#itemTotal()}
     * formula ({@code quantity_shipped * price}). The clerk edits {@code Price}, {@code Qty} and
     * {@code Qty Shipped} in place through the standard {@link Grid} editor; a double-click opens the
     * row. Editing is unbuffered and bound through a {@link Binder} (ADR-0002, no Signals): each
     * field's value-change listener writes the edit into the line and recomputes the row's item total
     * and the order {@code TOTAL} footer live (the recompute runs through {@link OrderPresenter}).
     */
    private void buildLineItems() {
        lineItems.addColumn(EditableLine::getItemId).setHeader("Item").setAutoWidth(true);
        Grid.Column<EditableLine> productColumn =
                lineItems.addColumn(EditableLine::getProductId).setHeader("Product").setAutoWidth(true);
        descriptionColumn = lineItems.addColumn(EditableLine::getProductName).setHeader("Description").setFlexGrow(1);
        Grid.Column<EditableLine> priceColumn =
                lineItems.addColumn(EditableLine::getPrice).setHeader("Price").setAutoWidth(true);
        Grid.Column<EditableLine> quantityColumn =
                lineItems.addColumn(EditableLine::getQuantity).setHeader("Qty").setAutoWidth(true);
        Grid.Column<EditableLine> quantityShippedColumn =
                lineItems.addColumn(EditableLine::getQuantityShipped).setHeader("Qty Shipped").setAutoWidth(true);
        itemTotalColumn = lineItems.addColumn(EditableLine::itemTotal).setHeader("Item Total").setAutoWidth(true);
        lineItems.setAllRowsVisible(true);
        lineItems.setSelectionMode(Grid.SelectionMode.SINGLE);

        // Unbuffered editor (ADR-0002): the Binder writes each field straight into the line as the
        // clerk leaves it — no Save/Cancel step. quantity_shipped and price drive ITEM_TOTAL.
        Editor<EditableLine> editor = lineItems.getEditor();
        Binder<EditableLine> binder = new Binder<>(EditableLine.class);
        editor.setBinder(binder);
        editor.setBuffered(false);

        BigDecimalField priceField = new BigDecimalField();
        IntegerField quantityField = new IntegerField();
        IntegerField quantityShippedField = new IntegerField();

        // The PRODUCTS_LOV (issue #17): a ComboBox over the catalog (id + name, ordered by name),
        // showing "id — name" but writing back only the id — a picker, not a defaulter. The small
        // Summit catalog is fetched once and held by id so a directly-typed id can be resolved.
        List<Product> products = presenter.products();
        Map<Long, Product> productsById = new LinkedHashMap<>();
        products.forEach(product -> productsById.put(product.id(), product));
        ComboBox<Product> productCombo = new ComboBox<>();
        productCombo.setId("line-product");
        productCombo.setItems(products);
        productCombo.setItemLabelGenerator(product -> product.id() + " — " + product.name());
        // Allow a directly-typed id (the Forms field accepted typing as well as the LOV): a known id
        // selects its product (re-defaulting via the listener below); an unknown one is rejected.
        productCombo.setAllowCustomValue(true);
        productColumn.setEditorComponent(productCombo);

        binder.forField(priceField).bind(EditableLine::getPrice, EditableLine::setPrice);
        // Quantities are domain longs; the IntegerField presents them and converts back, treating a
        // cleared field as zero so the line stays summable.
        binder.forField(quantityField)
                .withConverter(value -> value == null ? 0L : value.longValue(), Long::intValue)
                .bind(EditableLine::getQuantity, EditableLine::setQuantity);
        binder.forField(quantityShippedField)
                .withConverter(value -> value == null ? 0L : value.longValue(), Long::intValue)
                .bind(EditableLine::getQuantityShipped, EditableLine::setQuantityShipped);

        priceColumn.setEditorComponent(priceField);
        quantityColumn.setEditorComponent(quantityField);
        quantityShippedColumn.setEditorComponent(quantityShippedField);
        // Description (S_PRODUCT.name) is never typed — it is re-defaulted by the product change — so
        // in the editor it gets a read-only display that updates live when the product changes, the
        // same pattern as the item-total cell.
        descriptionEditor.setId("line-description");
        descriptionColumn.setEditorComponent(descriptionEditor);
        // ITEM_TOTAL is computed, never typed — but in the editor it gets a read-only display so the
        // row's item total visibly recomputes live as the clerk edits, without re-rendering (and
        // tearing down) the open editor.
        itemTotalColumn.setEditorComponent(itemTotalEditor);

        // Live recompute: registered after bind() so the Binder's unbuffered write runs first and
        // the bean already carries the new value when we re-total (ADR-0002 — listeners, not Signals).
        priceField.addValueChangeListener(event -> onLineEdited());
        quantityShippedField.addValueChangeListener(event -> onLineEdited());
        quantityField.addValueChangeListener(event -> onLineEdited());

        // The WHEN-VALIDATE-ITEM defaulter (issue #17): a product change — whether the clerk picks
        // from the LOV or types an id — writes back only the id (the LOV's job) and then re-defaults
        // the line's description and price from that product (the separate defaulter). The price
        // field is updated live so its own listener re-totals the row and the order footer.
        productCombo.addValueChangeListener(event -> {
            if (suppressProductDefaulter) {
                return;
            }
            Product picked = event.getValue();
            EditableLine edited = lineItems.getEditor().getItem();
            if (edited == null || picked == null) {
                return;
            }
            edited.setProductId(picked.id());                    // LOV return mapping: only the id
            presenter.applyProductDefaults(edited, picked.id()); // defaulter: description + price
            descriptionEditor.setText(nullSafe(edited.getProductName()));
            priceField.setValue(edited.getPrice());              // reflects the re-defaulted price...
            onLineEdited();                                      // ...and re-totals row + footer
        });
        // A directly-typed product id: a known id selects its product (which re-defaults through the
        // listener above); an unknown one is the Forms NO_DATA_FOUND reject — cleared, with a message.
        productCombo.addCustomValueSetListener(event -> {
            String raw = event.getDetail() == null ? "" : event.getDetail().trim();
            Long id = parseId(raw);
            Product match = id == null ? null : productsById.get(id);
            if (match != null) {
                productCombo.setValue(match);
            } else {
                productCombo.clear();
                Notification.show("No product with id " + raw + ".");
            }
        });

        // Seed the live item-total + description displays when the editor opens, and reflect the
        // current product in the LOV — suppressing the defaulter so loading an existing line does not
        // re-default it. Fold the final values back into the static cells on close (a refresh while
        // editing would close the open editor).
        editor.addOpenListener(event -> {
            EditableLine item = event.getItem();
            suppressProductDefaulter = true;
            productCombo.setValue(productsById.get(item.getProductId()));
            suppressProductDefaulter = false;
            descriptionEditor.setText(nullSafe(item.getProductName()));
            itemTotalEditor.setText(itemTotalText(item));
        });
        editor.addCloseListener(event -> {
            lineItems.getDataProvider().refreshItem(event.getItem());
            refreshOrderTotal();
        });

        lineItems.addItemDoubleClickListener(event -> editor.editItem(event.getItem()));

        addLine.setId("order-add-line");
        addLine.addClickListener(event -> addLineItem());
        removeLine.setId("order-remove-line");
        removeLine.setEnabled(false);
        removeLine.addClickListener(event -> removeSelectedLineItem());
        lineItems.asSingleSelect().addValueChangeListener(
                event -> removeLine.setEnabled(event.getValue() != null));
        lineActions.setPadding(false);
    }

    /**
     * Recomputes live as the clerk edits (the Binder has already written the field into the line):
     * the edited row's item-total display and the order {@code TOTAL} footer. It deliberately does
     * <em>not</em> refresh the grid row — doing so mid-edit re-renders and closes the open editor;
     * the static row cell is reconciled instead when the editor closes.
     */
    private void onLineEdited() {
        EditableLine edited = lineItems.getEditor().getItem();
        if (edited != null) {
            itemTotalEditor.setText(itemTotalText(edited));
        }
        refreshOrderTotal();
    }

    private static String itemTotalText(EditableLine line) {
        return line.itemTotal().toPlainString();
    }

    /** Appends a new line (next per-order item id) and opens its editor for the clerk to fill. */
    private void addLineItem() {
        EditableLine line = presenter.addLine();
        lineItems.getDataProvider().refreshAll();
        refreshOrderTotal();
        lineItems.getEditor().editItem(line);
    }

    /** Deletes the selected line (the in-memory detail delete), then re-totals. */
    private void removeSelectedLineItem() {
        EditableLine selected = lineItems.asSingleSelect().getValue();
        if (selected == null) {
            return;
        }
        if (lineItems.getEditor().isOpen()) {
            lineItems.getEditor().closeEditor();
        }
        presenter.removeLine(selected);
        lineItems.deselectAll();
        lineItems.getDataProvider().refreshAll();
        refreshOrderTotal();
    }

    /**
     * Refreshes the {@code TOTAL} footer from {@link OrderPresenter#currentTotal()} — the {@code Sum}
     * over the working lines' {@code ITEM_TOTAL}, recomputed live on every edit, add and delete.
     */
    private void refreshOrderTotal() {
        descriptionColumn.setFooter("Total");
        itemTotalColumn.setFooter(presenter.currentTotal().toPlainString());
    }

    /**
     * The Commit action ({@code COMMIT_FORM}, issue #19): hands the working header + lines to the
     * presenter's whole-graph save and surfaces its typed {@link CommitResult}. The Service is the
     * authoritative gate (ADR-0002); this only reflects the outcome. On success the form is rebound
     * to the reloaded (fresh {@code ORA_ROWSCN}) working state so a second commit does not falsely
     * conflict; a conflict or a gate rejection leaves the clerk's edits on screen to reconcile.
     */
    private void onCommit() {
        switch (presenter.commit()) {
            case CommitResult.Committed committed -> {
                rebindAfterCommit();
                Notification.show("Order #" + committed.orderId() + " committed.");
            }
            case CommitResult.Conflict ignored -> Notification.show(
                    "This order was changed by someone else since you loaded it — reload and try again.");
            case CommitResult.Rejected rejected -> Notification.show(rejected.message());
        }
    }

    /**
     * Rebinds the form to the reloaded working state after a successful commit: the presenter has
     * re-seeded a fresh working header + lines (with the moved {@code ORA_ROWSCN}), so the header
     * Binder and the grid must point at those new instances for any further editing to target — and
     * re-check against — the committed state.
     */
    private void rebindAfterCommit() {
        suppressHeaderListeners = true;
        headerBinder.setBean(presenter.editableHeader());
        suppressHeaderListeners = false;
        lineItems.getEditor().closeEditor();
        lineItems.setItems(presenter.editableLines());
        refreshOrderTotal();
    }

    private void showOrder(Order order) {
        // Seed the form from the loaded order. The bound fields (dates, payment type, order-filled)
        // come from the working header via setBean; the PK and the lookup-defaulted display fields
        // (customer/sales-rep names + sales-rep id) are set directly. Listeners are suppressed
        // throughout so seeding never fires the customer defaulter or the credit guard.
        suppressHeaderListeners = true;
        orderId.setValue(String.valueOf(order.id()));
        customerId.setValue(String.valueOf(order.customerId()));
        customerId.setInvalid(false);
        customerId.setErrorMessage(null);
        customerName.setValue(nullSafe(order.customerName()));
        salesRepId.setValue(order.salesRepId() == null ? "" : String.valueOf(order.salesRepId()));
        salesRepName.setValue(nullSafe(order.salesRepName()));
        headerBinder.setBean(presenter.editableHeader());
        suppressHeaderListeners = false;

        // S_ORD_S_ITEM coordination: the editable grid is reloaded with the picked order's working
        // lines (the presenter seeded a fresh working set on load).
        lineItems.getEditor().closeEditor();
        lineItems.setItems(presenter.editableLines());

        // Forms TOTAL (Summary=Sum over ITEM_TOTAL) → a footer under the Item Total column, fed from
        // the presenter's live total so it tracks the working lines as they are edited (ADR-0001).
        refreshOrderTotal();

        emptyHint.setVisible(false);
        headerSection.setVisible(true);
        lineItems.setVisible(true);
        lineActions.setVisible(true);
        commit.setEnabled(true);
        refreshPager();
    }

    private void clearHeader() {
        presenter.clear();
        headerSection.setVisible(false);
        lineItems.setVisible(false);
        lineActions.setVisible(false);
        commit.setEnabled(false);
        lineItems.getEditor().closeEditor();
        lineItems.setItems(java.util.Collections.emptyList());
        emptyHint.setText(NO_ORDER_HINT);
        emptyHint.setVisible(true);
        refreshPager();
    }

    /**
     * An advanced search that matched nothing — the cursor is already cleared by the presenter,
     * so the pager hides; the hint explains the empty result rather than leaving a stale header.
     */
    private void showNoMatches() {
        headerSection.setVisible(false);
        lineItems.setVisible(false);
        lineActions.setVisible(false);
        commit.setEnabled(false);
        lineItems.getEditor().closeEditor();
        lineItems.setItems(java.util.Collections.emptyList());
        emptyHint.setText(NO_MATCH_HINT);
        emptyHint.setVisible(true);
        refreshPager();
    }

    /** Clear (issue #14): resets every criterion — quick and advanced — and the navigation. */
    private void clearSearch() {
        customerFilter.clear();
        salesRepFilter.clear();
        paymentTypeFilter.clear();
        orderedFromFilter.clear();
        orderedToFilter.clear();
        statusFilter.clear();
        lastSearchTerm = "";
        quickSearch.clear();
        clearHeader();
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    /** Parses a typed id (customer or product), or {@code null} if it is not a number (then rejected). */
    private static Long parseId(String raw) {
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException | NullPointerException notANumber) {
            return null;
        }
    }
}
