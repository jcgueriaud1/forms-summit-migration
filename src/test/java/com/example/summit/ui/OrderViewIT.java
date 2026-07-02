package com.example.summit.ui;

import com.example.summit.it.support.OracleSpringPlaywrightIT;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.Test;
import org.vaadin.addons.dramafinder.element.BigDecimalFieldElement;
import org.vaadin.addons.dramafinder.element.ButtonElement;
import org.vaadin.addons.dramafinder.element.CheckboxElement;
import org.vaadin.addons.dramafinder.element.ComboBoxElement;
import org.vaadin.addons.dramafinder.element.DatePickerElement;
import org.vaadin.addons.dramafinder.element.GridElement;
import org.vaadin.addons.dramafinder.element.RadioButtonGroupElement;
import org.vaadin.addons.dramafinder.element.TextFieldElement;

import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UI integration test for {@link OrderView} (PRD #38). Drives the running app in a real
 * browser (Playwright + DramaFinder) against a real Summit-seeded Oracle container and
 * asserts both dimensions of the migrated construct:
 *
 * <ul>
 *   <li><strong>Behavioural parity</strong> — quick-search a single {@code S_ORD}
 *       order, pick a suggestion, and the read-only header fills, with customer and
 *       sales-rep display names resolved through the join (the dissolved POST-QUERY
 *       lookups), end-to-end through View &larr; Presenter &larr; Service &larr; Data.</li>
 *   <li><strong>Layout fidelity</strong> (ADR-0012/0013) — each header field renders as
 *       its <em>mapped</em> control in a read-only state: Payment Type is asserted via
 *       {@link RadioButtonGroupElement} (a {@code <vaadin-radio-group>}, not a text
 *       field), Order Filled via {@link CheckboxElement}, the dates via
 *       {@link DatePickerElement}. Locating those elements at all proves the type
 *       survived the read-only slice.</li>
 * </ul>
 *
 * <p>{@link #testLayoutFidelityGate()} is the <strong>layout-fidelity UI gate</strong>
 * (ADR-0012): it asserts the {@code CV_ORDER} structure survived the migration — the
 * "Order Information" {@code H2} heading is present, the header fields sit in
 * {@code layoutPlan} order inside the titled {@code RECT59} section, Order Id sits
 * outside that frame, and the payment type is grouped with the line items in the
 * {@code RECT60} section as its mapped {@code RadioButtonGroup}.
 */
public class OrderViewIT extends OracleSpringPlaywrightIT {

    @Override
    public String getView() {
        return "orders";
    }

    @Test
    public void testTitle() {
        assertThat(page).hasTitle("Orders | Summit Migration POC");
    }

    /**
     * Layout-fidelity UI gate (ADR-0012). Loads an order and asserts the {@code CV_ORDER}
     * structure survived: the "Order Information" H2 boilerplate heading renders as an
     * {@code <h2>}; the header fields sit in {@code layoutPlan} order inside the titled
     * {@code RECT59} section; Order Id sits outside that frame (layoutPlan section null);
     * and the payment type is grouped with the line items in the {@code RECT60} section
     * as its mapped {@code RadioButtonGroup}. Complements the per-field component-type
     * assertions in {@link #testPickingASuggestionLoadsTheReadOnlyHeaderAsMappedControls()}.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testLayoutFidelityGate() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Find order");
        search.filterAndSelectItem("50001", "#50001 — Unisports (Magee)");
        TextFieldElement.getByLabel(page, "Order Id").assertValue("50001");

        // The CV_ORDER "Order Information" boilerplate static text (role: heading,
        // headingLevel h2) renders as a real <h2>, not a dropped label (boilerplate-heading).
        assertThat(page.getByRole(AriaRole.HEADING,
                new Page.GetByRoleOptions().setName("Order Information").setLevel(2))).isVisible();

        // RECT59 → titled grouping section (frame-group): the enclosed header fields sit in
        // layoutPlan (Y-then-X) order, never declaration order.
        List<String> headerFieldOrder = (List<String>) page.locator("#order-header-fields > *")
                .evaluateAll("els => els.map(e => e.label).filter(label => label)");
        assertEquals(
                List.of("Date Ordered", "Customer Id", "Customer Name",
                        "Sales Rep Id", "Sales Rep Name",
                        "Date Shipped", "Order Filled"),
                headerFieldOrder);

        // Order Id is outside the RECT59 frame (layoutPlan section null) — present in the
        // view but not inside the "Order Information" section.
        assertEquals(0, page.locator("#order-information-section")
                .getByLabel("Order Id").count());

        // RECT60 → groups the payment type with the line-item region; Payment Type keeps
        // its mapped RadioButtonGroup shape (ADR-0013), located inside the items section.
        assertEquals(1, page.locator("#order-items-section vaadin-radio-group").count());
        assertEquals(1, page.locator("#order-items-section vaadin-grid").count());
    }

    @Test
    public void testHeaderHiddenUntilAnOrderIsPicked() {
        // Before any search the empty-state hint shows in place of the header form.
        assertThat(page.getByText("Search for an order to load its header.")).isVisible();
    }

    @Test
    public void testPickingASuggestionLoadsTheReadOnlyHeaderAsMappedControls() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Find order");
        // Server-side autocomplete: typing runs the quick search through the presenter;
        // picking the suggestion loads exactly one order into the read-only header.
        search.filterAndSelectItem("50001", "#50001 — Unisports (Magee)");

        // Behavioural parity: customer and sales-rep names arrive joined on the row —
        // beside their ids, no per-field round-trip (the headline of loadGraph).
        TextFieldElement.getByLabel(page, "Order Id").assertValue("50001");
        TextFieldElement.getByLabel(page, "Customer Id").assertValue("201");
        TextFieldElement.getByLabel(page, "Customer Name").assertValue("Unisports");
        TextFieldElement.getByLabel(page, "Sales Rep Id").assertValue("1");
        TextFieldElement.getByLabel(page, "Sales Rep Name").assertValue("Magee");

        // Layout fidelity (ADR-0013): the dates, the radio group, and the checkbox each
        // render as their mapped control — never flattened to a read-only TextField.
        DatePickerElement.getByLabel(page, "Date Ordered").assertValue(LocalDate.of(1992, 8, 31));
        DatePickerElement.getByLabel(page, "Date Shipped").assertValue(LocalDate.of(1992, 9, 10));
        RadioButtonGroupElement.getByLabel(page, "Payment Type").assertValue("Cash");
        CheckboxElement.getByLabel(page, "Order Filled").assertChecked();

        // Capture the loaded view for the PR / docs (real app, real Oracle, real browser).
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(Paths.get("target/orders-view.png"))
                .setFullPage(true));
    }

    @Test
    public void testLoadingAnOrderPopulatesTheReadOnlyLineItemsGrid() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Find order");
        search.filterAndSelectItem("50001", "#50001 — Unisports (Magee)");
        // Wait for the load round-trip to finish before reading the coordinated grid.
        TextFieldElement.getByLabel(page, "Order Id").assertValue("50001");

        GridElement grid = GridElement.get(page);

        // Layout-fidelity gate (ADR-0012): every in-scope column, in the form's order.
        assertEquals(
                List.of("Item", "Product", "Description", "Price", "Qty", "Qty Shipped", "Item Total"),
                grid.getHeaderCellContents());

        // Behavioural parity: the order's two S_ITEM lines load, product descriptions
        // resolved through the S_PRODUCT join — beside their ids, no per-line lookup.
        assertEquals(2, grid.getTotalRowCount());
        assertEquals("1", cell(grid, 0, "Item"));
        assertEquals("41010", cell(grid, 0, "Product"));
        assertEquals("Ski Boots", cell(grid, 0, "Description"));
        assertEquals("50.00", cell(grid, 0, "Price"));
        assertEquals("5", cell(grid, 0, "Qty"));
        assertEquals("5", cell(grid, 0, "Qty Shipped"));
        assertEquals("250.00", cell(grid, 0, "Item Total"));
        assertEquals("Ski Pole", cell(grid, 1, "Description"));
        assertEquals("351.00", cell(grid, 1, "Item Total"));

        // The order Total footer is the Domain TOTAL summary (ADR-0001): 250.00 + 351.00.
        assertThat(page.locator("#order-total")).hasText("601.00");

        page.screenshot(new Page.ScreenshotOptions()
                .setPath(Paths.get("target/orders-view.png"))
                .setFullPage(true));
    }

    @Test
    public void testSwitchingOrdersReplacesTheLineItemsGrid() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Find order");

        search.filterAndSelectItem("50001", "#50001 — Unisports (Magee)");
        TextFieldElement.getByLabel(page, "Order Id").assertValue("50001");
        GridElement grid = GridElement.get(page);
        assertEquals("Ski Boots", cell(grid, 0, "Description"));

        // S_ORD_S_ITEM coordination: picking a different order swaps the grid's contents.
        search.filterAndSelectItem("50002", "#50002 — Womansport (Giljum)");
        TextFieldElement.getByLabel(page, "Order Id").assertValue("50002");
        assertEquals(2, grid.getTotalRowCount());
        assertEquals("Pro Ski Boot", cell(grid, 0, "Description"));
        assertEquals("8000.00", cell(grid, 0, "Item Total"));

        // The Total footer recomputes for the switched order: 8000.00 + 56.60.
        assertThat(page.locator("#order-total")).hasText("8056.60");
    }

    @Test
    public void testOrderWithNoLineItemsShowsAnEmptyGrid() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Find order");
        // 50003 has a header but no S_ITEM rows — the coordinated grid shows no rows.
        search.filterAndSelectItem("50003", "#50003 — Unisports (no rep)");
        TextFieldElement.getByLabel(page, "Order Id").assertValue("50003");

        assertEquals(0, GridElement.get(page).getTotalRowCount());
        // An order with no lines totals to zero (Order.total() over an empty line list).
        assertThat(page.locator("#order-total")).hasText("0.00");
    }

    /**
     * Prev/next pager (issue #42) — the record-scrollbar replacement. Searching "Unisports"
     * returns two orders (50001, 50003, ordered by id). Picking the first lands at "Record 1
     * of 2" with Prev disabled; Next steps to the second order (its header and grid reload),
     * lands at "Record 2 of 2" with Next disabled; Prev steps back. The indicator and the
     * end-of-set disabling both reflect the presenter-owned position.
     */
    @Test
    public void testPrevNextPagerStepsThroughTheResultSet() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Find order");
        // Two orders share the customer "Unisports" — a multi-record result set to walk.
        search.filterAndSelectItem("Unisports", "#50001 — Unisports (Magee)");
        TextFieldElement.getByLabel(page, "Order Id").assertValue("50001");

        ButtonElement previous = ButtonElement.getByText(page, "Previous");
        ButtonElement next = ButtonElement.getByText(page, "Next");

        // First record: Prev disabled at the front, Next open, indicator reads "1 of 2".
        assertThat(page.locator("#order-record-indicator")).hasText("Record 1 of 2");
        assertThat(previous.getLocator()).isDisabled();
        assertThat(next.getLocator()).isEnabled();

        // Step to the last record: the whole graph reloads onto order 50003.
        next.click();
        TextFieldElement.getByLabel(page, "Order Id").assertValue("50003");
        assertThat(page.locator("#order-record-indicator")).hasText("Record 2 of 2");
        assertThat(next.getLocator()).isDisabled();
        assertThat(previous.getLocator()).isEnabled();

        // Step back to the first record.
        previous.click();
        TextFieldElement.getByLabel(page, "Order Id").assertValue("50001");
        assertThat(page.locator("#order-record-indicator")).hasText("Record 1 of 2");
        assertThat(previous.getLocator()).isDisabled();
    }

    /**
     * A single-match search still shows the pager, but with both ends disabled — one record
     * is record 1 of 1, nowhere to step. Order 50002 (Womansport) is the only order for that
     * customer.
     */
    @Test
    public void testPagerAtBothEndsForASingleMatch() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Find order");
        search.filterAndSelectItem("Womansport", "#50002 — Womansport (Giljum)");
        TextFieldElement.getByLabel(page, "Order Id").assertValue("50002");

        assertThat(page.locator("#order-record-indicator")).hasText("Record 1 of 1");
        assertThat(ButtonElement.getByText(page, "Previous").getLocator()).isDisabled();
        assertThat(ButtonElement.getByText(page, "Next").getLocator()).isDisabled();
    }

    // ---- Inline line-item editing + New / Duplicate / Delete + live totals (issue #45) ----------
    // The S_ITEM detail block becomes editable: the clerk edits price/qty in place, adds a line
    // (assigned MAX(item_id)+1), duplicates one (KEY-DUPREC clear-list), and deletes one behind a
    // confirm — with the order TOTAL footer recomputing live.

    @Test
    public void testNewLineAssignsTheNextItemIdForThatOrder() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Find order");
        search.filterAndSelectItem("50001", "#50001 — Unisports (Magee)");
        TextFieldElement.getByLabel(page, "Order Id").assertValue("50001");

        GridElement grid = GridElement.get(page);
        assertEquals(2, grid.getTotalRowCount());

        // New line: 50001 has items 1 and 2, so the new line is item 3 (per-order MAX+1).
        ButtonElement.getByText(page, "New line").click();
        // The click round-trips to the server (addLine + grid refresh) before the row count updates.
        page.waitForCondition(() -> grid.getTotalRowCount() == 3);
        assertThat(grid.findRow(2).orElseThrow().getCell(0).getCellContentLocator()).hasText("3");
    }

    @Test
    public void testDuplicatingALineClonesItWithTheNextItemId() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Find order");
        search.filterAndSelectItem("50001", "#50001 — Unisports (Magee)");
        TextFieldElement.getByLabel(page, "Order Id").assertValue("50001");

        GridElement grid = GridElement.get(page);
        assertEquals(2, grid.getTotalRowCount());

        // Select the boots line and duplicate it — the copy is a new line (item 3), KEY-DUPREC cleared.
        grid.select(0);
        ButtonElement.getByText(page, "Duplicate line").click();
        page.waitForCondition(() -> grid.getTotalRowCount() == 3);
        assertThat(grid.findRow(2).orElseThrow().getCell(0).getCellContentLocator()).hasText("3");
    }

    @Test
    public void testDeletingALineAsksForConfirmationThenDropsIt() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Find order");
        search.filterAndSelectItem("50001", "#50001 — Unisports (Magee)");
        TextFieldElement.getByLabel(page, "Order Id").assertValue("50001");

        GridElement grid = GridElement.get(page);
        assertEquals(2, grid.getTotalRowCount());

        // Select the first line and delete it — the KEY-DELREC caution alert as a ConfirmDialog.
        grid.select(0);
        ButtonElement.getByText(page, "Delete line").click();
        assertThat(page.getByText("Do you want to delete this line order?")).isVisible();

        // Confirm ("Yes") performs the in-memory detail delete.
        ButtonElement.getByText(page, "Yes").click();
        page.waitForCondition(() -> grid.getTotalRowCount() == 1);
    }

    @Test
    public void testEditingAPriceRecomputesTheOrderTotalLive() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Find order");
        search.filterAndSelectItem("50001", "#50001 — Unisports (Magee)");
        TextFieldElement.getByLabel(page, "Order Id").assertValue("50001");

        // On load: 5 * 50.00 + 27 * 13.00 = 601.00 (the Domain TOTAL, via the presenter's live sum).
        assertThat(page.locator("#order-total")).hasText("601.00");

        // Double-click the first line's Price cell (column 3) to open the inline editor on the row.
        GridElement grid = GridElement.get(page);
        grid.findRow(0).orElseThrow().getCell(3).getCellContentLocator().dblclick();

        // The unbuffered editor's price field appears (the only big-decimal field on the page).
        Locator priceFieldLocator = page.locator("vaadin-big-decimal-field");
        assertThat(priceFieldLocator).isVisible();

        // Re-price the boots at 60: the bound field writes the edit (unbuffered) and the value-change
        // listeners re-total live, so the footer reflects 5 * 60.00 + 27 * 13.00 = 651.00.
        new BigDecimalFieldElement(priceFieldLocator).setValue("60");
        assertThat(page.locator("#order-total")).hasText("651.00");
    }

    /**
     * Product LOV + WHEN-VALIDATE-ITEM defaulter (issue #46). The line's product is picked from the
     * {@code PRODUCTS_LOV} — a ComboBox that lists each product as "id — name" but returns only the id.
     * Picking a different product re-defaults the line's description and price from that product, so the
     * order Total recomputes live (the defaulter is separate from the pick, fired on the product change).
     */
    @Test
    public void testPickingAProductFromTheLovReDefaultsDescriptionAndPrice() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Find order");
        search.filterAndSelectItem("50001", "#50001 — Unisports (Magee)");
        TextFieldElement.getByLabel(page, "Order Id").assertValue("50001");

        // On load the first line is product 41010 "Ski Boots" at 50.00; total 250.00 + 351.00 = 601.00.
        assertThat(page.locator("#order-total")).hasText("601.00");

        // Open the first line's editor on the Product cell (column 1) — the PRODUCTS_LOV surfaces.
        GridElement grid = GridElement.get(page);
        grid.findRow(0).orElseThrow().getCell(1).getCellContentLocator().dblclick();

        ComboBoxElement productLov = new ComboBoxElement(page.locator("#order-product-lov"));
        assertThat(productLov.getLocator()).isVisible();
        // The LOV displays the current product as "id — name" (both columns), returning only the id.
        productLov.assertValue("41010 — Ski Boots");

        // Pick a different product: the defaulter re-defaults price to 1000.00 (and description to
        // "Pro Ski Boot"), so the footer recomputes live to 5 * 1000.00 + 351.00 = 5351.00.
        productLov.filterAndSelectItem("Pro", "30421 — Pro Ski Boot");
        assertThat(page.locator("#order-total")).hasText("5351.00");
    }

    /** Reads the trimmed text of a body cell addressed by row index and column header. */
    private static String cell(GridElement grid, int row, String column) {
        return grid.findCell(row, column).orElseThrow()
                .getCellContentLocator().innerText().trim();
    }

    // ---- Advanced search: the QBE (Enter-Query) replacement (issue #43) --------------------------
    // A collapsible advanced panel adds the multi-field filters (customer, sales rep, payment type,
    // ordered-date range, status) that combine with the quick term via AND; Clear resets both. The seed:
    // 50001 (Unisports/Magee, CASH, filled), 50002 (Womansport/Giljum, CREDIT, unfilled),
    // 50003 (Unisports/no rep, CASH, unfilled).

    @Test
    public void testAdvancedSearchCombinesWithTheQuickTermAndWalksTheNarrowedSet() {
        // Quick "Unisports" alone matches 50001 and 50003 (picking the first fixes the quick term).
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Find order");
        search.filterAndSelectItem("Unisports", "#50001 — Unisports (Magee)");
        TextFieldElement.getByLabel(page, "Order Id").assertValue("50001");

        // Narrow it with the advanced Status = Open filter (AND), then re-run: only the unfilled order
        // 50003 remains — a single-record result set.
        page.locator("#order-advanced-search").getByText("Advanced search").click();
        new ComboBoxElement(page.locator("#order-adv-status")).filterAndSelectItem("Open", "Open");
        ButtonElement.getByText(page, "Search").click();

        TextFieldElement.getByLabel(page, "Order Id").assertValue("50003");
        assertThat(page.locator("#order-record-indicator")).hasText("Record 1 of 1");
    }

    @Test
    public void testAdvancedSearchByPaymentTypeAloneFindsTheMatchingOrder() {
        // No quick term — advanced-only search. Payment type = Credit matches order 50002 alone.
        page.locator("#order-advanced-search").getByText("Advanced search").click();
        new ComboBoxElement(page.locator("#order-adv-payment")).filterAndSelectItem("Credit", "Credit");
        ButtonElement.getByText(page, "Search").click();

        TextFieldElement.getByLabel(page, "Order Id").assertValue("50002");
        TextFieldElement.getByLabel(page, "Customer Name").assertValue("Womansport");
    }

    @Test
    public void testClearResetsQuickAndAdvancedCriteriaToTheEmptyState() {
        // Load an order via an advanced-only search, then Clear: both the loaded order and the advanced
        // filter reset, returning to the empty state.
        page.locator("#order-advanced-search").getByText("Advanced search").click();
        ComboBoxElement payment = new ComboBoxElement(page.locator("#order-adv-payment"));
        payment.filterAndSelectItem("Credit", "Credit");
        ButtonElement.getByText(page, "Search").click();
        TextFieldElement.getByLabel(page, "Order Id").assertValue("50002");

        ButtonElement.getByText(page, "Clear").click();

        // Back to the empty state: the header hint returns and the advanced payment filter is cleared.
        assertThat(page.getByText("Search for an order to load its header.")).isVisible();
        payment.assertValue("");
    }

    // ---- Master-header editing behaviours (issue #47) -------------------------------------------
    // The header becomes editable in place (a state flip on the mapped controls, ADR-0013): entering a
    // customer id defaults the name + sales rep, an unknown id is rejected, a CREDIT payment a
    // customer's rating cannot support is reset to CASH with a notice, and a ship date before the order
    // date is flagged. The rules are pure Domain; the data is fetched by the Service (ADR-0001/0002).

    @Test
    public void testEnteringACustomerIdDefaultsTheNameAndSalesRep() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Find order");
        search.filterAndSelectItem("50001", "#50001 — Unisports (Magee)");
        TextFieldElement.getByLabel(page, "Order Id").assertValue("50001");
        // Loaded as customer 201 Unisports / rep Magee.
        TextFieldElement.getByLabel(page, "Customer Name").assertValue("Unisports");

        // Retype the customer id as 202 (Womansport, rep 2 Giljum): the WHEN-VALIDATE-ITEM lookup
        // defaults the name and sales rep onto the (read-only) display fields.
        TextFieldElement customerId = TextFieldElement.getByLabel(page, "Customer Id");
        customerId.setValue("202");

        TextFieldElement.getByLabel(page, "Customer Name").assertValue("Womansport");
        TextFieldElement.getByLabel(page, "Sales Rep Id").assertValue("2");
        TextFieldElement.getByLabel(page, "Sales Rep Name").assertValue("Giljum");
        customerId.assertValid();
    }

    @Test
    public void testAnUnknownCustomerIdIsRejected() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Find order");
        search.filterAndSelectItem("50001", "#50001 — Unisports (Magee)");
        TextFieldElement.getByLabel(page, "Order Id").assertValue("50001");

        // An id that is not a real customer is the Forms NO_DATA_FOUND — rejected with a clear message.
        TextFieldElement customerId = TextFieldElement.getByLabel(page, "Customer Id");
        customerId.setValue("999999");

        customerId.assertInvalid();
        customerId.assertErrorMessage("No customer with id 999999");
    }

    @Test
    public void testCreditPaymentIsRefusedAndResetToCashForAPoorlyRatedCustomer() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Find order");
        search.filterAndSelectItem("50001", "#50001 — Unisports (Magee)");
        TextFieldElement.getByLabel(page, "Order Id").assertValue("50001");

        // Switch to customer 290 (Kam's Sporting Goods, POOR) then try to pay by CREDIT.
        TextFieldElement.getByLabel(page, "Customer Id").setValue("290");
        TextFieldElement.getByLabel(page, "Customer Name").assertValue("Kam's Sporting Goods");

        RadioButtonGroupElement payment = RadioButtonGroupElement.getByLabel(page, "Payment Type");
        // setValue checks the radio (fires the server-side value-change), unlike selectByValue.
        payment.setValue("Credit");

        // The credit guard refuses it: the payment is reset to CASH and a notice is shown.
        payment.assertValue("Cash");
        assertThat(page.getByText("Credit is not available for this customer", new Page.GetByTextOptions().setExact(false)))
                .isVisible();
    }

    @Test
    public void testCreditPaymentIsAllowedForAWellRatedCustomer() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Find order");
        // 50001 is customer 201 Unisports (EXCELLENT) — CREDIT is allowed, so it sticks.
        search.filterAndSelectItem("50001", "#50001 — Unisports (Magee)");
        TextFieldElement.getByLabel(page, "Order Id").assertValue("50001");

        RadioButtonGroupElement payment = RadioButtonGroupElement.getByLabel(page, "Payment Type");
        payment.setValue("Credit");

        // A GOOD/EXCELLENT customer keeps the CREDIT choice — the guard does not fire.
        payment.assertValue("Credit");
    }

    @Test
    public void testShipDateBeforeOrderDateIsFlagged() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Find order");
        // 50001: ordered 1992-08-31, shipped 1992-09-10.
        search.filterAndSelectItem("50001", "#50001 — Unisports (Magee)");
        TextFieldElement.getByLabel(page, "Order Id").assertValue("50001");

        DatePickerElement dateShipped = DatePickerElement.getByLabel(page, "Date Shipped");
        dateShipped.assertValid();

        // Set the ship date before the order date — the WHEN-VALIDATE-RECORD guard flags it invalid.
        dateShipped.setValue(LocalDate.of(1992, 8, 30));

        dateShipped.assertInvalid();
        dateShipped.assertErrorMessage("Ship date cannot be before the order date");
    }

    @Test
    public void testCreditOrderShowsCreditRadioSelected() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Find order");
        // 50002 is a CREDIT order — the radio group's Credit option is the selected one.
        search.filterAndSelectItem("50002", "#50002 — Womansport (Giljum)");

        RadioButtonGroupElement.getByLabel(page, "Payment Type").assertValue("Credit");
        CheckboxElement.getByLabel(page, "Order Filled").assertNotChecked();
    }

    @Test
    public void testOrderWithNoSalesRepLoadsWithBlankRep() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Find order");
        // 50003 has a null SALES_REP_ID — the LEFT JOIN keeps the row; the rep shows blank.
        search.filterAndSelectItem("50003", "#50003 — Unisports (no rep)");

        TextFieldElement.getByLabel(page, "Order Id").assertValue("50003");
        TextFieldElement.getByLabel(page, "Sales Rep Name").assertValue("");
        CheckboxElement.getByLabel(page, "Order Filled").assertNotChecked();
    }

    // ---- Master record actions: New / Duplicate / Delete order (issue #49) ----------------------
    // The S_ORD master gains its record-action family, derived from the block's crud capability
    // (blockActionClassifiers), not from a KEY-* trigger: New opens a blank editable order, Duplicate
    // clones the loaded one as an unsaved insert (KEY-DUPREC clear-list), and Delete confirms then removes
    // it — composed with the Non-Isolated delete-cascade-guard (disabled while line items exist). The
    // actual insert/delete persistence is proven against the DB in OrderServiceIT; these assert the UI.

    @Test
    public void testNewOrderOpensABlankEditableOrder() {
        // New order is reachable straight from the empty state (no search) — the new-item factory.
        ButtonElement.getByText(page, "New order").click();

        // A blank editable order: no id yet (drawn from S_ORD_ID.nextval on commit), no customer, no
        // lines, zero total — the clerk fills it in and commits.
        TextFieldElement.getByLabel(page, "Order Id").assertValue("");
        TextFieldElement.getByLabel(page, "Customer Id").assertValue("");
        GridElement grid = GridElement.get(page);
        page.waitForCondition(() -> grid.getTotalRowCount() == 0);
        assertThat(page.locator("#order-total")).hasText("0.00");

        // A brand-new, unsaved order has nothing to delete — Delete order stays disabled.
        assertThat(ButtonElement.getByText(page, "Delete order").getLocator()).isDisabled();
    }

    @Test
    public void testDuplicateOrderClonesTheLoadedOrderAsAnUnsavedInsert() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Find order");
        search.filterAndSelectItem("50001", "#50001 — Unisports (Magee)");
        TextFieldElement.getByLabel(page, "Order Id").assertValue("50001");
        GridElement grid = GridElement.get(page);
        assertEquals(2, grid.getTotalRowCount());

        // Duplicate: the KEY-DUPREC clear-list clones the order into a new, unsaved one.
        ButtonElement.getByText(page, "Duplicate order").click();

        // The id clears (it commits as an insert), but the customer and the two lines are carried over
        // for the clerk to review before Commit.
        TextFieldElement.getByLabel(page, "Order Id").assertValue("");
        TextFieldElement.getByLabel(page, "Customer Id").assertValue("201");
        page.waitForCondition(() -> grid.getTotalRowCount() == 2);
        assertThat(page.getByText("Duplicated order", new Page.GetByTextOptions().setExact(false)))
                .isVisible();
    }

    @Test
    public void testDeleteOrderIsDisabledWhileLineItemsExist() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Find order");
        // 50001 has two S_ITEM lines — the Non-Isolated delete-cascade-guard vetoes the master delete,
        // surfaced as a disabled Delete order button (S_ITEM detail rows can never be orphaned).
        search.filterAndSelectItem("50001", "#50001 — Unisports (Magee)");
        TextFieldElement.getByLabel(page, "Order Id").assertValue("50001");

        assertThat(ButtonElement.getByText(page, "Delete order").getLocator()).isDisabled();
    }

    @Test
    public void testDeleteOrderConfirmsForAChildlessOrder() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Find order");
        // 50003 has a header but no S_ITEM rows — a childless order the guard lets through.
        search.filterAndSelectItem("50003", "#50003 — Unisports (no rep)");
        TextFieldElement.getByLabel(page, "Order Id").assertValue("50003");

        ButtonElement deleteOrder = ButtonElement.getByText(page, "Delete order");
        assertThat(deleteOrder.getLocator()).isEnabled();

        // Confirming would delete; this asserts the KEY-DELREC caution alert appears, then cancels so the
        // shared Summit seed stays intact (the delete-through persistence is proven in OrderServiceIT).
        deleteOrder.click();
        assertThat(page.getByText("Do you want to delete this order?")).isVisible();
        ButtonElement.getByText(page, "No").click();

        // Cancelled — the order is still loaded, nothing deleted.
        TextFieldElement.getByLabel(page, "Order Id").assertValue("50003");
    }
}
