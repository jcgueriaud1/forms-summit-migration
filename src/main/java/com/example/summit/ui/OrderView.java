package com.example.summit.ui;

import com.example.summit.domain.CustomerHeader;
import com.example.summit.domain.Order;
import com.example.summit.domain.OrderSummary;
import com.example.summit.domain.Product;
import com.example.summit.presenter.EditableHeader;
import com.example.summit.presenter.EditableLine;
import com.example.summit.presenter.OrderPresenter;
import com.example.summit.service.SaveResult;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.FooterRow;
import com.vaadin.flow.component.grid.editor.Editor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * are hoisted into the surrounding layout. Its {@code PRODUCT_LOV_BUTTON} is dissolved into
 * the line's product {@link ComboBox} itself — the LOV is the field, not a separate popup
 * button (issue #46). Its other buttons (stock, image, help, exit) remain out of this slice.
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
 * field renders as the control the classifier mapped for it — Payment Type a
 * {@link RadioButtonGroup} (Cash/Credit), Order Filled a {@link Checkbox}, the dates
 * {@link DatePicker}s — never flattened to a {@link TextField}. Making the header editable
 * (issue #47) is a <em>state flip</em> on those same mapped controls, not a component swap: the
 * editable fields (customer id, the two dates, payment type, order-filled) leave the read-only
 * state, while the lookup-filled display fields (order id, customer name, sales rep id/name) stay
 * read-only.
 *
 * <p><strong>Header editing behaviours (issue #47).</strong> The editable fields bind to an
 * {@link EditableHeader} working copy through a {@link Binder} (ADR-0002, no Signals), which folds in
 * the {@code S_ORD} Forms validation behaviours. Entering a <em>customer id</em> runs the
 * {@code WHEN-VALIDATE-ITEM} lookup ({@link OrderPresenter#lookupCustomer}): a real customer defaults
 * its name and sales rep onto the header; an id that is not a real customer is rejected with a clear
 * message on the field. Selecting a <em>CREDIT</em> payment runs the credit guard
 * ({@link OrderPresenter#paymentAllowed}, delegating to the pure Domain
 * {@link com.example.summit.domain.PaymentEligibility}): a customer whose rating is not GOOD or
 * EXCELLENT has the payment refused and reset to CASH with a notice. The <em>ship date</em> is guarded
 * by a Binder validator delegating to {@link Order#shipDateValid(java.time.LocalDate, java.time.LocalDate)}
 * — a ship date before the order date is flagged invalid. Persisting these edits is the commit slice's
 * job (#48); this slice keeps the header editing in memory.
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

    // The editable header (issue #47): a Binder over the working EditableHeader copy folds in the
    // S_ORD Forms validation behaviours (customer lookup, credit guard, ship-date guard). ADR-0002:
    // Binder + value-change listeners, no Signals. The bean is seeded on load and edited in memory.
    private final Binder<EditableHeader> headerBinder = new Binder<>(EditableHeader.class);
    private EditableHeader header;
    // Guards the header's value-change listeners (customer lookup, credit guard) while the view seeds
    // the fields on load or resets a refused payment, so those programmatic writes do not re-fire the
    // behaviours as if the clerk had made the edit.
    private boolean syncingHeader = false;

    private final HorizontalLayout toolbar = new HorizontalLayout();
    private final ComboBox<OrderSummary> search = new ComboBox<>("Find order");
    // Prev/next pager (issue #42) — the record-scrollbar replacement. Position lives in the
    // presenter; these controls render "record N of M" and disable at the result-set ends.
    private final Button previousButton = new Button("Previous");
    private final Button nextButton = new Button("Next");
    private final Span recordIndicator = new Span();
    private final HorizontalLayout pager = new HorizontalLayout();
    // The KEY-COMMIT keystroke as a toolbar action (issue #48): one Commit persists the whole graph —
    // header + lines — through the Service's @Transactional whole-graph save, under the ORA_ROWSCN gate.
    private final Button commitButton = new Button("Commit");
    private final H2 orderInformationHeading = new H2("Order Information");
    private final FormLayout headerFields = new FormLayout();
    private final Div orderInformationSection = new Div();
    private final Div itemsSection = new Div();
    private final Grid<EditableLine> lineItems = new Grid<>();
    // The order Total footer value (ADR-0001 TOTAL summary) — recomputed live from the presenter's
    // working lines (Order.total()) on load and on every edit/add/duplicate/delete; the grid never sums.
    private final Span orderTotalValue = new Span();
    // Read-only display of the edited row's ITEM_TOTAL, shown while its editor is open so the row
    // total recomputes live without re-rendering (and tearing down) the open editor.
    private final Span itemTotalEditor = new Span();
    // The PRODUCTS_LOV (issue #46): the line's product picker — a ComboBox showing "id — name" and
    // returning only the id, fed by the presenter's service-backed reference list. Editing the price
    // in place needs the field beyond buildLineEditor (the defaulter reflects the defaulted price into
    // it), so it and the ComboBox are view fields.
    private final ComboBox<Product> productField = new ComboBox<>();
    private final BigDecimalField priceField = new BigDecimalField();
    // The LOV reference list indexed by id, so opening the editor can show the line's current product
    // and a directly typed / picked id can resolve to its display row.
    private final Map<Long, Product> productsById = new HashMap<>();
    // Guards the product field's value-change listener while the editor syncs it to the current line,
    // so seeding or reflecting a resolved product does not re-fire the WHEN-VALIDATE-ITEM defaulter.
    private boolean syncingProduct = false;
    // Detail-block record actions (issue #45), derived from S_ITEM.crud (blockActionClassifiers):
    // New appends a blank line (next per-order item id), Duplicate clones the selected line
    // (KEY-DUPREC clear-list), Delete removes it after a confirm. Duplicate/Delete need a selection.
    private final Button newLine = new Button("New line");
    private final Button duplicateLine = new Button("Duplicate line");
    private final Button deleteLine = new Button("Delete line");
    private final HorizontalLayout lineActions = new HorizontalLayout(newLine, duplicateLine, deleteLine);
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
        buildCommit();
        toolbar.add(search, pager, commitButton);
    }

    /**
     * The Forms {@code KEY-COMMIT} keystroke ({@code COMMIT_FORM;}) as a web action (issue #48): a
     * Commit button, also bound to {@code Ctrl+S}, that fires the single whole-graph
     * {@code @Transactional} save. It only <em>invokes</em> the save; the persistence, the
     * {@code ORA_ROWSCN} concurrency gate, and the validation gate all live in the Service.
     */
    private void buildCommit() {
        commitButton.setId("order-commit");
        commitButton.addClickListener(event -> commit());
        commitButton.addClickShortcut(Key.KEY_S, KeyModifier.CONTROL);
    }

    /**
     * Drives the whole-graph commit: flush any open line editor, block on the header Binder's
     * validators (the live mirror of the Service gate), then hand the edited header to the presenter.
     * The typed {@link SaveResult} decides the feedback — reflect the saved order, warn about a
     * concurrent change (no overwrite), or point at the invalid input.
     */
    private void commit() {
        if (header == null) {
            return;
        }
        if (lineItems.getEditor().isOpen()) {
            lineItems.getEditor().closeEditor();
        }
        if (customerId.isInvalid() || headerBinder.validate().hasErrors()) {
            Notification.show("Fix the highlighted fields before committing.");
            return;
        }
        switch (presenter.commit(header)) {
            case SaveResult.Committed committed -> {
                showOrder(committed.order());
                Notification.show("Order #" + committed.order().id() + " committed.");
            }
            case SaveResult.Conflict ignored -> Notification.show(
                    "This order was changed by someone else since you loaded it. "
                            + "Reload it to see the latest, then re-apply your changes.");
            case SaveResult.Invalid invalid -> Notification.show(invalid.message());
        }
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
        // choice — so it stays a RadioButtonGroup (ADR-0013), showing the Forms labels
        // "Cash"/"Credit" over the S_ORD.PAYMENT_TYPE codes.
        paymentType.setItems(PAYMENT_TYPES);
        paymentType.setItemLabelGenerator(OrderView::paymentTypeLabel);

        // The lookup-filled display fields stay read-only: Order Id is the PK, and Customer Name /
        // Sales Rep Id / Sales Rep Name are defaulted by the customer lookup, never typed (ADR-0013 —
        // a read-only state on the mapped control, not a component swap).
        orderId.setReadOnly(true);
        customerName.setReadOnly(true);
        salesRepId.setReadOnly(true);
        salesRepName.setReadOnly(true);
        // The editable fields (issue #47) are the same mapped controls, flipped out of read-only:
        // customer id, the two dates, payment type, order-filled — a state flip, not a component swap.
        customerId.setId("order-customer-id");

        buildHeaderBinder();

        // Fields placed in layoutPlan order (Y-then-X), never declaration order (ADR-0012).
        headerFields.add(dateOrdered, customerId, customerName,
                salesRepId, salesRepName,
                dateShipped, orderFilled);
    }

    /**
     * Wires the header {@link Binder} and the {@code S_ORD} Forms editing behaviours (issue #47),
     * following ADR-0002 (Binder + value-change listeners, no Signals):
     * <ul>
     *   <li><strong>Customer lookup</strong> ({@code WHEN-VALIDATE-ITEM}) — customer id is driven by a
     *       value-change listener rather than a Binder write, because a customer change re-defaults
     *       several fields at once (name, sales rep). A real customer defaults them in; an unknown id
     *       is rejected on the field.</li>
     *   <li><strong>Credit guard</strong> — a listener on the payment radio refuses a CREDIT payment a
     *       customer's rating does not support, resetting it to CASH with a notice.</li>
     *   <li><strong>Ship-date guard</strong> ({@code WHEN-VALIDATE-RECORD}) — a Binder validator on the
     *       ship date delegates to the pure Domain {@link Order#shipDateValid}; a change to the order
     *       date re-validates it so the cross-field rule stays live.</li>
     * </ul>
     */
    private void buildHeaderBinder() {
        headerBinder.forField(dateOrdered).bind(EditableHeader::getDateOrdered, EditableHeader::setDateOrdered);
        headerBinder.forField(dateShipped)
                .withValidator(shipped -> Order.shipDateValid(dateOrdered.getValue(), shipped),
                        "Ship date cannot be before the order date")
                .bind(EditableHeader::getDateShipped, EditableHeader::setDateShipped);
        headerBinder.forField(paymentType).bind(EditableHeader::getPaymentType, EditableHeader::setPaymentType);
        headerBinder.forField(orderFilled).bind(EditableHeader::isOrderFilled, EditableHeader::setOrderFilled);

        // The order date is one half of the cross-field ship-date rule: when it changes, re-run
        // validation so the ship-date field's error reflects the new comparison (it is the validated
        // field, so its own change already re-validates).
        dateOrdered.addValueChangeListener(event -> {
            if (!syncingHeader) {
                headerBinder.validate();
            }
        });

        // Customer id (not bound): a change re-defaults name + sales rep, so it runs the lookup
        // defaulter, mirroring the product LOV's handling of a multi-field defaulter.
        customerId.addValueChangeListener(event -> {
            if (!syncingHeader) {
                onCustomerIdChanged(event.getValue());
            }
        });

        // Payment type (bound): the credit guard runs after the Binder has written the choice.
        paymentType.addValueChangeListener(event -> {
            if (!syncingHeader) {
                enforceCreditEligibility();
            }
        });
    }

    /**
     * The {@code S_ORD} {@code WHEN-VALIDATE-ITEM} customer lookup as the view drives it: parse the
     * entered id, look the customer up through the presenter, and on success default its name and sales
     * rep onto the header (then re-check credit eligibility, since a CREDIT order may no longer be
     * allowed for the new customer). A non-numeric or unknown id is the Forms {@code NO_DATA_FOUND}:
     * rejected with a clear message on the customer-id field.
     */
    private void onCustomerIdChanged(String entered) {
        if (header == null) {
            return;
        }
        String text = entered == null ? "" : entered.trim();
        long customerId;
        try {
            customerId = Long.parseLong(text);
        } catch (NumberFormatException notAnId) {
            rejectCustomer(text);
            return;
        }
        Optional<CustomerHeader> found = presenter.lookupCustomer(customerId);
        if (found.isEmpty()) {
            rejectCustomer(text);
            return;
        }
        this.customerId.setInvalid(false);
        header.setCustomerId(customerId);
        applyCustomerDisplay(found.get());
        enforceCreditEligibility();
    }

    /** Rejects an unknown / non-numeric customer entry with a clear message on the customer-id field. */
    private void rejectCustomer(String entered) {
        customerId.setErrorMessage("No customer with id " + entered);
        customerId.setInvalid(true);
    }

    /** Defaults the looked-up customer's name and sales rep onto the (read-only) display fields and bean. */
    private void applyCustomerDisplay(CustomerHeader customer) {
        header.setCustomerName(customer.name());
        header.setSalesRepId(customer.salesRepId());
        header.setSalesRepName(customer.salesRepName());
        customerName.setValue(nullSafe(customer.name()));
        salesRepId.setValue(customer.salesRepId() == null ? "" : String.valueOf(customer.salesRepId()));
        salesRepName.setValue(nullSafe(customer.salesRepName()));
    }

    /**
     * The credit guard: a CREDIT payment the current customer's rating does not support is refused and
     * reset to CASH with a notice (the Forms credit check). The decision is the pure Domain
     * {@link com.example.summit.domain.PaymentEligibility} rule, reached through
     * {@link OrderPresenter#paymentAllowed}; CASH is never gated. Runs on both a payment change and a
     * customer change, so switching to a poorly-rated customer also drops an outstanding CREDIT choice.
     */
    private void enforceCreditEligibility() {
        if (header == null || !"CREDIT".equals(paymentType.getValue())) {
            return;
        }
        if (presenter.paymentAllowed(header.getCustomerId(), "CREDIT")) {
            return;
        }
        syncingHeader = true;
        try {
            paymentType.setValue("CASH");
            header.setPaymentType("CASH");
        } finally {
            syncingHeader = false;
        }
        Notification.show("Credit is not available for this customer (rating not GOOD or EXCELLENT). "
                + "Payment type reset to Cash.");
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
        // frame title / boilerplate heading sits on it, so the section carries no heading. The
        // detail record actions sit under the grid; the whole section shares the order's visibility.
        itemsSection.setId("order-items-section");
        itemsSection.addClassName("panel");
        lineActions.setPadding(false);
        itemsSection.add(paymentType, lineItems, lineActions);
    }

    /**
     * The editable line-items grid (issue #45). Columns follow the form's order (ADR-0012): Item,
     * Product, Description, Price, Qty, Qty Shipped, Item Total — the {@code S_ITEM} block as the
     * clerk reads it. {@code Price}, {@code Qty} and {@code Qty Shipped} are edited in place through
     * the standard unbuffered {@link Grid} editor bound with a {@link Binder} (ADR-0002, no Signals;
     * Grid Pro would add a commercial dependency the harness can't drive — the standard editor is the
     * established pattern here). A double-click opens the row's editor; each field's value-change
     * listener writes the edit into the {@link EditableLine} and recomputes the row's item total and
     * the order {@code TOTAL} footer live through the {@link OrderPresenter}. {@code Item Total} and
     * the {@code Total} footer stay the pure Domain formulae ({@link EditableLine#itemTotal()} /
     * {@link Order#total(java.util.List)}), never summed in the view.
     */
    private void buildLineItems() {
        lineItems.addColumn(EditableLine::getItemId).setHeader("Item").setAutoWidth(true);
        Grid.Column<EditableLine> productColumn = lineItems.addColumn(line -> productDisplay(line.getProductId()))
                .setHeader("Product").setAutoWidth(true);
        lineItems.addColumn(EditableLine::getDescription).setHeader("Description").setAutoWidth(true).setFlexGrow(1);
        Grid.Column<EditableLine> priceColumn = lineItems.addColumn(line -> money(line.getPrice()))
                .setHeader("Price").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        Grid.Column<EditableLine> quantityColumn = lineItems.addColumn(EditableLine::getQuantity)
                .setHeader("Qty").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        Grid.Column<EditableLine> quantityShippedColumn = lineItems.addColumn(EditableLine::getQuantityShipped)
                .setHeader("Qty Shipped").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        Grid.Column<EditableLine> itemTotalColumn = lineItems.addColumn(line -> money(line.itemTotal()))
                .setHeader("Item Total").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);

        // The TOTAL summary as a grid footer: a static "Order Total" label over the shipped column,
        // and the live Domain-computed amount under the Item Total column (refreshOrderTotal).
        FooterRow footer = lineItems.appendFooterRow();
        footer.getCell(quantityShippedColumn).setText("Order Total");
        orderTotalValue.setId("order-total");
        footer.getCell(itemTotalColumn).setComponent(orderTotalValue);

        // A short, header-coordinated detail block — size to its rows rather than scroll inside a
        // fixed viewport, so the whole order reads at a glance.
        lineItems.setAllRowsVisible(true);
        lineItems.setWidthFull();
        lineItems.setSelectionMode(Grid.SelectionMode.SINGLE);

        buildLineEditor(productColumn, priceColumn, quantityColumn, quantityShippedColumn, itemTotalColumn);
        buildLineActions();
    }

    /**
     * Wires the unbuffered {@link Grid} editor (ADR-0002): a {@link Binder} writes each field straight
     * into the {@link EditableLine} as the clerk leaves it — no Save/Cancel step — and the
     * value-change listeners (registered <em>after</em> {@code bind()} so the write lands first)
     * recompute the row item total and the order footer live. {@code Item Total} is computed, never
     * typed, so it gets a read-only display in the editor; a refresh while the editor is open would
     * tear it down, so the static cell is reconciled on the editor's close instead.
     */
    private void buildLineEditor(Grid.Column<EditableLine> productColumn,
                                 Grid.Column<EditableLine> priceColumn,
                                 Grid.Column<EditableLine> quantityColumn,
                                 Grid.Column<EditableLine> quantityShippedColumn,
                                 Grid.Column<EditableLine> itemTotalColumn) {
        Editor<EditableLine> editor = lineItems.getEditor();
        Binder<EditableLine> binder = new Binder<>(EditableLine.class);
        editor.setBinder(binder);
        editor.setBuffered(false);

        IntegerField quantityField = new IntegerField();
        IntegerField quantityShippedField = new IntegerField();

        buildProductLov();

        binder.forField(priceField).bind(EditableLine::getPrice, EditableLine::setPrice);
        // Quantities are domain longs; the IntegerField presents them and converts back, treating a
        // cleared field as zero so the line stays summable.
        binder.forField(quantityField)
                .withConverter(value -> value == null ? 0L : value.longValue(), Long::intValue)
                .bind(EditableLine::getQuantity, EditableLine::setQuantity);
        binder.forField(quantityShippedField)
                .withConverter(value -> value == null ? 0L : value.longValue(), Long::intValue)
                .bind(EditableLine::getQuantityShipped, EditableLine::setQuantityShipped);

        // The product field is NOT bound through the Binder: a product change re-defaults the
        // description and price too, so the WHEN-VALIDATE-ITEM defaulter writes the whole line at once
        // (applyProductChange) rather than the Binder writing the bare id.
        productColumn.setEditorComponent(productField);
        priceColumn.setEditorComponent(priceField);
        quantityColumn.setEditorComponent(quantityField);
        quantityShippedColumn.setEditorComponent(quantityShippedField);
        itemTotalColumn.setEditorComponent(itemTotalEditor);

        priceField.addValueChangeListener(event -> onLineEdited());
        quantityField.addValueChangeListener(event -> onLineEdited());
        quantityShippedField.addValueChangeListener(event -> onLineEdited());

        editor.addOpenListener(event -> {
            EditableLine line = event.getItem();
            itemTotalEditor.setText(money(line.itemTotal()));
            // Seed the LOV with the line's current product (empty for a blank new line), without
            // re-firing the defaulter, and clear any stale rejection from a prior edit.
            syncingProduct = true;
            try {
                productField.setValue(productsById.get(line.getProductId()));
            } finally {
                syncingProduct = false;
            }
            productField.setInvalid(false);
        });
        editor.addCloseListener(event -> {
            lineItems.getDataProvider().refreshItem(event.getItem());
            refreshOrderTotal();
        });
        lineItems.addItemDoubleClickListener(event -> editor.editItem(event.getItem()));
    }

    /**
     * Builds the {@code PRODUCTS_LOV} (issue #46) — the line's product picker. It is fed the presenter's
     * service-backed reference list ({@code select id, name from s_product order by name}) once at view
     * init (the Forms record-group populate) and shows each row as "id — name" while returning only the
     * id, so the popup lists both columns but {@code S_ITEM.PRODUCT_ID} receives just the id.
     *
     * <p>Two paths drive the same defaulter: picking a row fires {@code valueChange}, and typing an id
     * directly and committing it fires {@code customValueSet} — both route to {@link #applyProductChange}
     * so the description/price re-default on <em>any</em> {@code product_id} change, LOV pick or direct
     * entry.
     */
    private void buildProductLov() {
        List<Product> products = presenter.availableProducts();
        products.forEach(product -> productsById.put(product.id(), product));
        productField.setId("order-product-lov");
        productField.setItems(products);
        productField.setItemLabelGenerator(Product::label);
        // Allow a directly typed product id (the Forms text-item entry), not only a dropdown pick.
        productField.setAllowCustomValue(true);
        productField.setWidthFull();

        productField.addValueChangeListener(event -> {
            if (syncingProduct || event.getValue() == null) {
                return;
            }
            applyProductChange(event.getValue().id());
        });
        productField.addCustomValueSetListener(event -> onProductTyped(event.getDetail()));
    }

    /**
     * Handles a directly typed product id (the LOV's {@code customValueSet}). A numeric entry runs the
     * same {@link #applyProductChange} defaulter; anything non-numeric is rejected on the field, since
     * only a product id — not a name — is a valid direct entry.
     */
    private void onProductTyped(String typed) {
        String text = typed == null ? "" : typed.trim();
        try {
            applyProductChange(Long.parseLong(text));
        } catch (NumberFormatException notAnId) {
            rejectProduct(text);
        }
    }

    /**
     * The {@code WHEN-VALIDATE-ITEM} defaulter as the view drives it: hand the changed {@code product_id}
     * to the presenter, which re-defaults the working line's description and price from the product. On
     * success the resolved product and defaulted price are reflected into the open editor and the totals
     * recompute; an id that is not a real product is rejected with a clear message on the field (the
     * Forms {@code NO_DATA_FOUND → RAISE}).
     */
    private void applyProductChange(long productId) {
        EditableLine line = lineItems.getEditor().getItem();
        if (line == null) {
            return;
        }
        if (!presenter.applyProductDefaults(line, productId)) {
            rejectProduct(String.valueOf(productId));
            return;
        }
        productField.setInvalid(false);
        // Reflect the resolved product row and the defaulted price back into the open editor.
        syncingProduct = true;
        try {
            productField.setValue(productsById.get(productId));
        } finally {
            syncingProduct = false;
        }
        priceField.setValue(line.getPrice());
        onLineEdited();
    }

    /** Rejects an unknown / non-numeric product entry with a clear message on the LOV field. */
    private void rejectProduct(String entered) {
        productField.setErrorMessage("No product with id " + entered);
        productField.setInvalid(true);
    }

    /**
     * The detail record actions (issue #45), derived from {@code S_ITEM.crud} not from a KEY-* trigger
     * ({@code blockActionClassifiers}). New appends a blank line and opens its editor; Duplicate clones
     * the selected line via the Domain {@code KEY-DUPREC} clear-list and opens the copy; Delete removes
     * the selected line after a {@link ConfirmDialog} (the Forms {@code KEY-DELREC} caution alert).
     */
    private void buildLineActions() {
        newLine.setId("order-new-line");
        newLine.addClickListener(event -> addLineItem());

        duplicateLine.setId("order-duplicate-line");
        duplicateLine.setEnabled(false);
        duplicateLine.addClickListener(event -> duplicateSelectedLine());

        deleteLine.setId("order-delete-line");
        deleteLine.setEnabled(false);
        deleteLine.addClickListener(event -> confirmDeleteSelectedLine());

        lineItems.asSingleSelect().addValueChangeListener(event -> {
            boolean hasSelection = event.getValue() != null;
            duplicateLine.setEnabled(hasSelection);
            deleteLine.setEnabled(hasSelection);
        });
    }

    /**
     * Recomputes live as the clerk edits (the Binder has already written the field into the line): the
     * edited row's item-total display and the order {@code TOTAL} footer. It deliberately does
     * <em>not</em> refresh the grid row — doing so mid-edit re-renders and closes the open editor; the
     * static row cell is reconciled instead when the editor closes.
     */
    private void onLineEdited() {
        EditableLine edited = lineItems.getEditor().getItem();
        if (edited != null) {
            itemTotalEditor.setText(money(edited.itemTotal()));
        }
        refreshOrderTotal();
    }

    /** Appends a new line (next per-order item id) and opens its editor for the clerk to fill. */
    private void addLineItem() {
        EditableLine line = presenter.addLine();
        lineItems.getDataProvider().refreshAll();
        refreshOrderTotal();
        lineItems.getEditor().editItem(line);
    }

    /** Clones the selected line (KEY-DUPREC clear-list) into a new insert and opens its editor. */
    private void duplicateSelectedLine() {
        EditableLine selected = lineItems.asSingleSelect().getValue();
        if (selected == null) {
            return;
        }
        EditableLine copy = presenter.duplicateLine(selected);
        lineItems.getDataProvider().refreshAll();
        refreshOrderTotal();
        lineItems.getEditor().editItem(copy);
    }

    /**
     * The {@code KEY-DELREC} caution alert as a {@link ConfirmDialog} — a destructive action warrants
     * a confirm on the web. Confirming performs the in-memory detail delete and re-totals.
     */
    private void confirmDeleteSelectedLine() {
        EditableLine selected = lineItems.asSingleSelect().getValue();
        if (selected == null) {
            return;
        }
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Delete line");
        dialog.setText("Do you want to delete this line order?");
        dialog.setCancelable(true);
        dialog.setCancelText("No");
        dialog.setConfirmText("Yes");
        dialog.addConfirmListener(event -> deleteLine(selected));
        dialog.open();
    }

    /** Removes a line from the working set (the in-memory detail delete), then re-totals. */
    private void deleteLine(EditableLine selected) {
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
     * over the working lines' {@code ITEM_TOTAL}, recomputed live on every edit, add, duplicate, and
     * delete (the authoritative Domain formula, ADR-0001).
     */
    private void refreshOrderTotal() {
        orderTotalValue.setText(money(presenter.currentTotal()));
    }

    /** Renders a money amount with two decimals; a null amount shows blank. */
    private static String money(BigDecimal amount) {
        return amount == null ? "" : String.format("%.2f", amount);
    }

    /** The Product column's read-only display — the id, blank for an unproducted (new) line. */
    private static String productDisplay(long productId) {
        return productId == 0 ? "" : String.valueOf(productId);
    }

    private static String paymentTypeLabel(String code) {
        return switch (code) {
            case "CASH" -> "Cash";
            case "CREDIT" -> "Credit";
            default -> code;
        };
    }

    private void showOrder(Order order) {
        // Seed the header working copy and reflect it into the fields. syncingHeader guards the
        // customer-lookup and credit-guard listeners so seeding a loaded (already-valid) order does not
        // re-fire them as if the clerk had typed the values — a loaded CREDIT order must not be reset.
        syncingHeader = true;
        try {
            header = new EditableHeader(order);
            orderId.setValue(String.valueOf(order.id()));
            customerId.setValue(String.valueOf(order.customerId()));
            customerId.setInvalid(false);
            salesRepId.setValue(order.salesRepId() == null ? "" : String.valueOf(order.salesRepId()));
            customerName.setValue(nullSafe(order.customerName()));
            salesRepName.setValue(nullSafe(order.salesRepName()));
            // The Binder populates the editable fields (dates, payment type, order-filled) from the bean.
            headerBinder.setBean(header);
        } finally {
            syncingHeader = false;
        }

        // S_ORD_S_ITEM coordination: the editable grid is reloaded with the picked order's working
        // lines (the presenter seeded a fresh working set on load); any open editor is discarded.
        lineItems.getEditor().closeEditor();
        lineItems.setItems(presenter.editableLines());
        // The order Total footer is fed from the presenter's live total (ADR-0001), so it tracks the
        // working lines as they are edited/added/removed.
        refreshOrderTotal();

        emptyHint.setVisible(false);
        setOrderVisible(true);
    }

    private void clearHeader() {
        presenter.reset();
        // Detach the header working copy so the editing listeners have nothing to act on until the
        // next order loads, and clear any stale customer-id rejection.
        syncingHeader = true;
        try {
            headerBinder.removeBean();
            header = null;
            customerId.setInvalid(false);
        } finally {
            syncingHeader = false;
        }
        lineItems.getEditor().closeEditor();
        setOrderVisible(false);
        lineItems.setItems(List.of());
        emptyHint.setVisible(true);
    }

    /** Shows or hides the loaded-order regions (Order Id + both frame sections) and the Commit action
     * together — there is nothing to commit until an order is loaded. */
    private void setOrderVisible(boolean visible) {
        orderId.setVisible(visible);
        orderInformationSection.setVisible(visible);
        itemsSection.setVisible(visible);
        commitButton.setVisible(visible);
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
