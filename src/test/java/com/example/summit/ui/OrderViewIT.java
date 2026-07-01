package com.example.summit.ui;

import com.example.summit.it.support.OracleSpringPlaywrightIT;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;
import org.vaadin.addons.dramafinder.element.CheckboxElement;
import org.vaadin.addons.dramafinder.element.ComboBoxElement;
import org.vaadin.addons.dramafinder.element.DatePickerElement;
import org.vaadin.addons.dramafinder.element.RadioButtonGroupElement;
import org.vaadin.addons.dramafinder.element.TextFieldElement;

import java.nio.file.Paths;
import java.time.LocalDate;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * UI integration test for {@link OrderView} — the first real form migration (PRD #38,
 * Step 1a). Drives the running app in a real browser (Playwright + DramaFinder) against
 * a real Summit-seeded Oracle container and asserts both dimensions of the migrated
 * construct:
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
