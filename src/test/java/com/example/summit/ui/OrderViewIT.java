package com.example.summit.ui;

import com.example.summit.it.support.OracleSpringPlaywrightIT;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.Test;
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
    }

    @Test
    public void testOrderWithNoLineItemsShowsAnEmptyGrid() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Find order");
        // 50003 has a header but no S_ITEM rows — the coordinated grid shows no rows.
        search.filterAndSelectItem("50003", "#50003 — Unisports (no rep)");
        TextFieldElement.getByLabel(page, "Order Id").assertValue("50003");

        assertEquals(0, GridElement.get(page).getTotalRowCount());
    }

    /** Reads the trimmed text of a body cell addressed by row index and column header. */
    private static String cell(GridElement grid, int row, String column) {
        return grid.findCell(row, column).orElseThrow()
                .getCellContentLocator().innerText().trim();
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
}
