package com.example.summit.ui;

import com.example.summit.it.support.OracleSpringPlaywrightIT;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;
import org.vaadin.addons.dramafinder.element.ButtonElement;
import org.vaadin.addons.dramafinder.element.CheckboxElement;
import org.vaadin.addons.dramafinder.element.ComboBoxElement;
import org.vaadin.addons.dramafinder.element.DatePickerElement;
import org.vaadin.addons.dramafinder.element.GridElement;
import org.vaadin.addons.dramafinder.element.NotificationElement;
import org.vaadin.addons.dramafinder.element.RadioButtonGroupElement;
import org.vaadin.addons.dramafinder.element.TextFieldElement;

import com.microsoft.playwright.options.AriaRole;

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
}
