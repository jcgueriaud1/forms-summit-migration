package com.example.summit.ui;

import com.example.summit.it.support.OracleSpringPlaywrightIT;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;
import org.vaadin.addons.dramafinder.element.BigDecimalFieldElement;
import org.vaadin.addons.dramafinder.element.ButtonElement;
import org.vaadin.addons.dramafinder.element.CheckboxElement;
import org.vaadin.addons.dramafinder.element.ComboBoxElement;
import org.vaadin.addons.dramafinder.element.DatePickerElement;
import org.vaadin.addons.dramafinder.element.DetailsElement;
import org.vaadin.addons.dramafinder.element.GridElement;
import org.vaadin.addons.dramafinder.element.NotificationElement;
import org.vaadin.addons.dramafinder.element.RadioButtonGroupElement;
import org.vaadin.addons.dramafinder.element.TextFieldElement;

import com.microsoft.playwright.options.AriaRole;

import java.math.BigDecimal;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UI integration test for {@link OrderView} — the first real form migration (PRD #8,
 * Step 1a). Drives the running app in a real browser (Playwright + DramaFinder) against
 * a real Summit-seeded Oracle container and asserts the user-visible behavior the clerk
 * recognizes from Forms: quick-search a single {@code S_ORD} order, pick a suggestion,
 * and the read-only header form fills — with customer and sales-rep <em>display names</em>
 * resolved through the join (the dissolved POST-QUERY lookups), end-to-end through
 * View &larr; Presenter &larr; Service &larr; Data.
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

    @Test
    public void testControlBlockButtonsAreHoistedIntoTheToolbar() {
        // The CONTROL block dissolves (block-control → LayoutHost): its toolbar-docked buttons
        // are hoisted into the surrounding layout, keeping their Forms labels.
        ButtonElement.getByText(page, "Stock").assertVisible();
        ButtonElement.getByText(page, "Image On").assertVisible();
        ButtonElement.getByText(page, "Help").assertVisible();
        ButtonElement exit = ButtonElement.getByText(page, "Exit");
        exit.assertVisible();

        // Their behaviours are deferred to later slices, surfaced honestly rather than faked.
        exit.click();
        NotificationElement.getByText(page, "Exit — not part of this slice.").assertOpen();
    }

    @Test
    public void testHeaderHiddenUntilAnOrderIsPicked() {
        // Before any search the empty-state hint shows in place of the header form.
        assertThat(page.getByText("Search for an order to load its header.")).isVisible();
    }

    @Test
    public void testPickingASuggestionLoadsTheReadOnlyHeaderWithJoinedNames() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Find order");
        // Server-side autocomplete: typing runs the quick search through the presenter;
        // picking the suggestion loads exactly one order into the read-only header.
        search.filterAndSelectItem("50001", "#50001 — Unisports (Magee)");

        // Customer and sales-rep names arrive joined on the row — beside their ids, no
        // per-field round-trip (the headline of loadGraph).
        TextFieldElement.getByLabel(page, "Order Id").assertValue("50001");
        TextFieldElement.getByLabel(page, "Customer Id").assertValue("201");
        TextFieldElement.getByLabel(page, "Customer Name").assertValue("Unisports");
        TextFieldElement.getByLabel(page, "Sales Rep Id").assertValue("1");
        TextFieldElement.getByLabel(page, "Sales Rep Name").assertValue("Magee");
        // PAYMENT_TYPE is a Forms Radio Group → RadioButtonGroup, read-only (ADR-0012/0013):
        // its selected value is CASH, not a text field reading "CASH".
        RadioButtonGroupElement.getByLabel(page, "Payment Type").assertValue("CASH");
        DatePickerElement.getByLabel(page, "Date Ordered").assertValue(LocalDate.of(1992, 8, 31));
        CheckboxElement.getByLabel(page, "Order Filled").assertChecked();

        // Capture the loaded view for the PR / docs (real app, real Oracle, real browser).
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(Paths.get("target/orders-view.png"))
                .setFullPage(true));
    }

    @Test
    public void testLoadingAnOrderShowsItsLineItemsAndSwitchingReplacesThem() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Find order");
        search.filterAndSelectItem("50001", "#50001 — Unisports (Magee)");

        GridElement grid = GridElement.get(page);
        grid.waitForGridToStopLoading();

        // The columns the clerk recognizes from the Forms S_ITEM block.
        assertEquals(List.of("Item", "Product", "Description", "Price", "Qty", "Qty Shipped", "Item Total"),
                grid.getHeaderCellContents());

        // 50001 has two lines, ordered by item_id, the product description joined onto the row.
        assertEquals(2, grid.getTotalRowCount());
        assertThat(grid.findRow(0).orElseThrow().getCell(2).getCellContentLocator()).hasText("Ski Boots");
        assertThat(grid.findRow(1).orElseThrow().getCell(2).getCellContentLocator()).hasText("Ski Pole");

        // S_ORD_S_ITEM coordination: picking another order replaces the grid contents.
        search.filterAndSelectItem("50002", "#50002 — Womansport (Giljum)");
        grid.waitForGridToStopLoading();
        assertEquals(2, grid.getTotalRowCount());
        assertThat(grid.findRow(0).orElseThrow().getCell(2).getCellContentLocator()).hasText("Pro Ski Boot");
    }

    @Test
    public void testOrderTotalFooterShowsTheDomainComputedSum() {
        // The order Total footer (Forms TOTAL = Sum over ITEM_TOTAL) is fed from Order.total()
        // (ADR-0001), so it reflects quantity_shipped * price summed across the lines.
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Find order");
        search.filterAndSelectItem("50001", "#50001 — Unisports (Magee)");

        GridElement grid = GridElement.get(page);
        grid.waitForGridToStopLoading();

        // Sanity: the order actually loaded before we look at its footer.
        TextFieldElement.getByLabel(page, "Order Id").assertValue("50001");

        // 50001: 5 * 50 + 27 * 13 = 601, under a "Total" footer label (the value renders at the
        // NUMBER's canonical scale — whole sums show no trailing ".00").
        assertThat(page.getByText("Total", new Page.GetByTextOptions().setExact(true))).isVisible();
        assertThat(page.getByText("601", new Page.GetByTextOptions().setExact(true))).isVisible();

        // Switching orders recomputes the footer: 50002 -> 8 * 1000 + 1 * 56.6 = 8056.6 (the
        // 56.60 cents survive — they are summed, not rounded away).
        search.filterAndSelectItem("50002", "#50002 — Womansport (Giljum)");
        grid.waitForGridToStopLoading();
        assertThat(page.getByText("8056.6", new Page.GetByTextOptions().setExact(true))).isVisible();
    }

    // ---- Prev/next pager over the result set (issue #13) --------------------------------
    // The replacement for Forms' record scrollbar: a quick search returns a result set, and
    // prev/next step through it one order at a time with a "record N of M" indicator. The two
    // Unisports orders (50001, 50003), ordered by id, are the result set here.

    private ButtonElement previousRecord() {
        return ButtonElement.getByText(page, new Page.GetByRoleOptions().setName("Previous"));
    }

    private ButtonElement nextRecord() {
        return ButtonElement.getByText(page, new Page.GetByRoleOptions().setName("Next"));
    }

    @Test
    public void testPagerHiddenUntilAnOrderIsLoaded() {
        // Before any search there is no result set to step, so the pager is not shown.
        assertThat(nextRecord().getLocator()).isHidden();
        assertThat(page.getByText("Record 1 of")).isHidden();
    }

    @Test
    public void testPagerStepsThroughTheSearchResultSet() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Find order");
        // "Unisports" matches two orders (50001, 50003); picking the first opens that result set.
        search.filterAndSelectItem("Unisports", "#50001 — Unisports (Magee)");

        // Positioned at the first match: "record 1 of 2", prev disabled, next enabled.
        assertThat(page.getByText("Record 1 of 2")).isVisible();
        previousRecord().assertDisabled();
        nextRecord().assertEnabled();

        // Step forward: the next order in the set loads into the same read-only header...
        nextRecord().click();
        TextFieldElement.getByLabel(page, "Order Id").assertValue("50003");
        TextFieldElement.getByLabel(page, "Sales Rep Name").assertValue("");
        // ...now at the last match: "record 2 of 2", next disabled, prev enabled.
        assertThat(page.getByText("Record 2 of 2")).isVisible();
        nextRecord().assertDisabled();
        previousRecord().assertEnabled();

        // Step back to the first match — the header reloads the previous order.
        previousRecord().click();
        TextFieldElement.getByLabel(page, "Order Id").assertValue("50001");
        assertThat(page.getByText("Record 1 of 2")).isVisible();
        previousRecord().assertDisabled();
    }

    @Test
    public void testPagerReportsASingleMatchAsRecordOneOfOne() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Find order");
        // Womansport has just one order — a one-record set: both ends are disabled.
        search.filterAndSelectItem("Womansport", "#50002 — Womansport (Giljum)");

        assertThat(page.getByText("Record 1 of 1")).isVisible();
        previousRecord().assertDisabled();
        nextRecord().assertDisabled();
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

    // ---- Layout-fidelity gate (ADR-0012) -------------------------------------------------
    // The build-output completeness check, counterpart to the spec's form-object grain: every
    // in-scope field, frame, and heading must render — in the form's order, as its mapped
    // component type. These are the assertions that would have caught the four regressions a
    // behaviour-only test sailed past (dropped title, flattened radio, reordered fields).

    private void loadOrder() {
        ComboBoxElement.getByLabel(page, "Find order")
                .filterAndSelectItem("50001", "#50001 — Unisports (Magee)");
    }

    @Test
    public void testOrderInformationHeadingIsPresent() {
        // The "Order Information" boilerplate text (TEXT91) is a heading (boilerplate-heading
        // → H2), not a dropped label — it titles the frame's grouping section.
        loadOrder();
        assertThat(page.getByRole(AriaRole.HEADING,
                new Page.GetByRoleOptions().setName("Order Information"))).isVisible();
    }

    @Test
    public void testPaymentTypeRendersAsARadioGroupNotATextField() {
        loadOrder();
        // Mapped component type is preserved: Payment Type is a radio group...
        RadioButtonGroupElement payment = RadioButtonGroupElement.getByLabel(page, "Payment Type");
        payment.assertValue("CASH");
        // ...and is NOT a text field flattened from the radio (the original defect).
        assertEquals(0, page.locator("vaadin-text-field")
                .filter(new com.microsoft.playwright.Locator.FilterOptions().setHasText("Payment Type"))
                .count());
    }

    @Test
    public void testHeaderFieldOrderMatchesTheForm() {
        // Field order is the form's order (S_ORD layoutPlan, Y-then-X), not an invented order.
        // Read each header field's label in DOM document order (the search ComboBox and the Grid
        // are not field hosts, so they are excluded by the selector).
        loadOrder();
        // Wait for the loaded header to render, then read labels scoped to the section (so the
        // search ComboBox and the dev-tools banner are excluded).
        page.locator("#order-information").waitFor();
        @SuppressWarnings("unchecked")
        List<Object> raw = (List<Object>) page.locator(
                        "#order-information vaadin-text-field, #order-information vaadin-date-picker, "
                                + "#order-information vaadin-radio-group, #order-information vaadin-checkbox")
                .evaluateAll("els => els.map(e => e.label ?? null)");
        List<String> order = raw.stream().map(o -> (String) o).toList();
        assertEquals(List.of(
                "Order Id", "Date Ordered", "Customer Id", "Customer Name",
                "Sales Rep Id", "Sales Rep Name", "Date Shipped", "Order Filled", "Payment Type"),
                order);
    }

    // ---- Advanced search + navigation that reflects the result set (issue #14) --------------
    // The QBE replacement: the advanced panel's multi-field filters AND-narrow the lookup, and
    // running a search loads the matching set positioned at record 1 — so the pager always
    // reflects the current search (one match → one page) and resets on every search and on Clear.

    private void openAdvancedSearch() {
        DetailsElement.getBySummaryText(page, "Advanced search").setOpen(true);
    }

    /** The advanced fields share labels with the header (e.g. "Payment Type"), so scope to the panel. */
    private Locator advanced() {
        return page.locator("#advanced-search");
    }

    private void runSearch() {
        page.locator("#order-search").click();
    }

    @Test
    public void testAdvancedSearchLoadsTheResultSetWithNavigationReflectingIt() {
        openAdvancedSearch();
        // Customer "Unisports" matches two orders (50001, 50003); Search loads the set at record 1.
        TextFieldElement.getByLabel(advanced(), "Customer").setValue("Unisports");
        runSearch();

        TextFieldElement.getByLabel(page, "Order Id").assertValue("50001");
        assertThat(page.getByText("Record 1 of 2")).isVisible();
        previousRecord().assertDisabled();
        nextRecord().assertEnabled();
    }

    @Test
    public void testAdvancedSearchWithASingleMatchHasSinglePageNavigation() {
        openAdvancedSearch();
        // Only Womansport's order (50002) is CREDIT — a one-record set, so both ends are disabled.
        ComboBoxElement.getByLabel(advanced(), "Payment Type").selectItem("Credit");
        runSearch();

        TextFieldElement.getByLabel(page, "Order Id").assertValue("50002");
        assertThat(page.getByText("Record 1 of 1")).isVisible();
        previousRecord().assertDisabled();
        nextRecord().assertDisabled();
    }

    @Test
    public void testAdvancedSearchThatMatchesNothingShowsNoResultsAndNoPager() {
        openAdvancedSearch();
        TextFieldElement.getByLabel(advanced(), "Customer").setValue("No Such Customer");
        runSearch();

        assertThat(page.getByText("No orders match your search.")).isVisible();
        assertThat(nextRecord().getLocator()).isHidden();
    }

    @Test
    public void testRunningANewSearchResetsNavigationToTheFirstRecord() {
        openAdvancedSearch();
        TextFieldElement.getByLabel(advanced(), "Customer").setValue("Unisports");
        runSearch();

        // Step to the last record of the set...
        nextRecord().click();
        TextFieldElement.getByLabel(page, "Order Id").assertValue("50003");
        assertThat(page.getByText("Record 2 of 2")).isVisible();

        // ...then re-run the search: the cursor resets to record 1, not wherever it was left.
        runSearch();
        TextFieldElement.getByLabel(page, "Order Id").assertValue("50001");
        assertThat(page.getByText("Record 1 of 2")).isVisible();
        previousRecord().assertDisabled();
    }

    // ---- Inline line-item editing + add / delete + live totals (issue #16) ------------------
    // The S_ITEM detail block becomes editable: the clerk edits price/qty in place, adds a line
    // (assigned MAX(item_id)+1), and removes one — with the order TOTAL footer recomputing live.

    @Test
    public void testAddingALineAssignsTheNextItemIdForThatOrder() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Find order");
        search.filterAndSelectItem("50001", "#50001 — Unisports (Magee)");

        GridElement grid = GridElement.get(page);
        grid.waitForGridToStopLoading();
        assertEquals(2, grid.getTotalRowCount());

        // Add line: 50001 has items 1 and 2, so the new line is item 3 (per-order MAX+1).
        ButtonElement.getByText(page, "Add line").click();
        // The click round-trips to the server (addLine + grid refresh) before the row count updates.
        page.waitForCondition(() -> grid.getTotalRowCount() == 3);
        assertThat(grid.findRow(2).orElseThrow().getCell(0).getCellContentLocator()).hasText("3");
    }

    @Test
    public void testRemovingALineDropsItFromTheGrid() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Find order");
        search.filterAndSelectItem("50001", "#50001 — Unisports (Magee)");

        GridElement grid = GridElement.get(page);
        grid.waitForGridToStopLoading();
        assertEquals(2, grid.getTotalRowCount());

        // Select the first line and remove it — the detail in-memory delete.
        grid.select(0);
        ButtonElement.getByText(page, "Remove line").click();
        page.waitForCondition(() -> grid.getTotalRowCount() == 1);
    }

    @Test
    public void testEditingAPriceRecomputesTheOrderTotalLive() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Find order");
        search.filterAndSelectItem("50001", "#50001 — Unisports (Magee)");

        GridElement grid = GridElement.get(page);
        grid.waitForGridToStopLoading();
        // On load: 5 * 50 + 27 * 13 = 601.
        assertThat(page.getByText("601", new Page.GetByTextOptions().setExact(true))).isVisible();

        // Double-click the first line's Price cell (column 3) to open the inline editor on the row.
        grid.findRow(0).orElseThrow().getCell(3).getCellContentLocator().dblclick();

        // The unbuffered editor's price field appears (the only big-decimal field on the page).
        Locator priceFieldLocator = page.locator("vaadin-big-decimal-field");
        assertThat(priceFieldLocator).isVisible();

        // Re-price the boots at 60: the bound field writes the edit (unbuffered) and the value-change
        // listeners re-total live, so the footer reflects the new sum 5 * 60 + 27 * 13 = 651.
        new BigDecimalFieldElement(priceFieldLocator).setValue("60");
        assertThat(page.getByText("651", new Page.GetByTextOptions().setExact(true))).isVisible();
    }

    // ---- Product LOV + WHEN-VALIDATE-ITEM defaulter (issue #17) ------------------------------
    // The Product column's editor is the PRODUCTS_LOV: a ComboBox showing "id — name" that writes
    // back only the id, with the line's description and price re-defaulted from the chosen product
    // on any product_id change — picked from the LOV or typed directly. An invalid id is rejected.

    /** Loads order 50001 and opens the inline editor on its first line (the Product cell, column 1). */
    private GridElement openFirstLineEditor() {
        ComboBoxElement.getByLabel(page, "Find order")
                .filterAndSelectItem("50001", "#50001 — Unisports (Magee)");
        GridElement grid = GridElement.get(page);
        grid.waitForGridToStopLoading();
        // Wait until the first row has actually rendered before reaching into it (the rendered row
        // range lags the load-stopped signal, otherwise findRow can NPE on an empty range).
        page.waitForCondition(() -> grid.findRow(0).isPresent());
        grid.findRow(0).orElseThrow().getCell(1).getCellContentLocator().dblclick();
        return grid;
    }

    @Test
    public void testProductLovShowsIdAndNameAndHoldsTheCurrentProduct() {
        openFirstLineEditor();
        // The LOV displays id + name, and on open holds the line's current product (41010 Ski Boots).
        ComboBoxElement lov = new ComboBoxElement(page.locator("#line-product"));
        lov.assertValue("41010 — Ski Boots");
    }

    @Test
    public void testPickingAProductFromTheLovReDefaultsDescriptionAndPrice() {
        openFirstLineEditor();

        // Pick a different product: the WHEN-VALIDATE-ITEM defaulter re-defaults the line's
        // description (S_PRODUCT.name) and price (suggested_whlsl_price) from the chosen product.
        new ComboBoxElement(page.locator("#line-product")).selectItem("10011 — Bunny Boot");

        // Description re-defaults to the product name, shown live in the open editor...
        assertThat(page.locator("#line-description")).hasText("Bunny Boot");
        // ...and the price re-defaults to Bunny Boot's suggested wholesale price (56.60). Compared
        // numerically since the field renders the value at the NUMBER's natural scale ("56.6").
        assertEquals(0, new BigDecimal("56.60").compareTo(new BigDecimal(editedPrice())));
    }

    @Test
    public void testTypingAProductIdDirectlyReDefaultsTheLine() {
        openFirstLineEditor();

        // Direct entry (not a LOV pick): typing a valid product id re-defaults the line just the
        // same — the defaulter fires on any product_id change. 20108 is the Ski Pole (13.00).
        Locator lovInput = new ComboBoxElement(page.locator("#line-product")).getInputLocator();
        lovInput.fill("20108");
        lovInput.press("Enter");

        assertThat(page.locator("#line-description")).hasText("Ski Pole");
        assertEquals(0, new BigDecimal("13.00").compareTo(new BigDecimal(editedPrice())));
    }

    /** The current text in the open editor's price field (rendered at the NUMBER's natural scale). */
    private String editedPrice() {
        return new BigDecimalFieldElement(page.locator("vaadin-big-decimal-field")).getValue();
    }

    @Test
    public void testTypingAnUnknownProductIdIsRejected() {
        openFirstLineEditor();

        // An invalid (non-existent) product id is the Forms NO_DATA_FOUND reject: cleared, with a
        // clear message, and the line keeps its original product (Ski Boots) untouched.
        Locator lovInput = new ComboBoxElement(page.locator("#line-product")).getInputLocator();
        lovInput.fill("99999");
        lovInput.press("Enter");

        NotificationElement.getByText(page, "No product with id 99999.").assertOpen();
        assertThat(page.locator("#line-description")).hasText("Ski Boots");
    }

    @Test
    public void testClearResetsTheAdvancedCriteriaAndNavigation() {
        openAdvancedSearch();
        TextFieldElement.getByLabel(advanced(), "Customer").setValue("Unisports");
        ComboBoxElement.getByLabel(advanced(), "Payment Type").selectItem("Cash");
        runSearch();
        // A result set is loaded and the pager is showing it.
        assertThat(page.getByText("Record 1 of 2")).isVisible();

        page.locator("#order-clear").click();

        // Clear resets the criteria and the navigation: back to the empty state, no pager.
        assertThat(page.getByText("Search for an order to load its header.")).isVisible();
        assertThat(nextRecord().getLocator()).isHidden();
        TextFieldElement.getByLabel(advanced(), "Customer").assertValue("");
        ComboBoxElement.getByLabel(advanced(), "Payment Type").assertValue("");
    }
}
