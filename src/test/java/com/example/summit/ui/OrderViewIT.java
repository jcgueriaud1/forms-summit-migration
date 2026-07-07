package com.example.summit.ui;

import com.example.summit.it.support.OracleSpringPlaywrightIT;
import org.junit.jupiter.api.Test;
import org.vaadin.addons.dramafinder.element.CheckboxElement;
import org.vaadin.addons.dramafinder.element.ComboBoxElement;
import org.vaadin.addons.dramafinder.element.DatePickerElement;
import org.vaadin.addons.dramafinder.element.RadioButtonGroupElement;
import org.vaadin.addons.dramafinder.element.TextFieldElement;

import java.time.LocalDate;
import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UI integration test for {@link OrderView} — the ORDERS tracer bullet (issue #85),
 * driven in a real browser (Playwright + DramaFinder) against a real seeded Summit
 * Oracle. It exercises the construct end to end through
 * View &larr; Presenter &larr; Service &larr; Domain &larr; Data, in both dimensions
 * the migration tracks (CONTEXT.md): behavioural parity (quick search → single-order
 * header load with joined names) and layout fidelity (fields in the form's order,
 * each keeping its mapped control kind — ADR-0012/0013).
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
    public void testQuickSearchSuggestsMatchesOnIdCustomerAndSalesRep() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Find order");

        // Customer name: Unisports has two orders (50001, 50003).
        search.setFilter("Unisports");
        search.assertItemCount(2);

        // Sales rep name: Henry Giljum reps only order 50002.
        search.setFilter("Giljum");
        search.assertItemCount(1);

        // Order id.
        search.setFilter("50002");
        search.assertItemCount(1);
    }

    @Test
    public void testSelectingASuggestionLoadsTheSingleReadOnlyHeader() {
        // Pick one suggestion by order id (unambiguous — one match).
        ComboBoxElement.getByLabel(page, "Find order").filterAndSelectItem("50001", "50001");

        // The chosen order's header — a single record, not a list — with the customer
        // and sales-rep names joined in on the one loadGraph read (POST-QUERY dissolved).
        TextFieldElement.getByLabel(page, "Order Id").assertValue("50001");
        TextFieldElement.getByLabel(page, "Customer Id").assertValue("201");
        TextFieldElement.getByLabel(page, "Customer Name").assertValue("Unisports");
        TextFieldElement.getByLabel(page, "Sales Rep Id").assertValue("1");
        TextFieldElement.getByLabel(page, "Sales Rep Name").assertValue("Colin Magee");
        DatePickerElement.getByLabel(page, "Date Ordered").assertValue(LocalDate.of(1992, 8, 31));
        DatePickerElement.getByLabel(page, "Date Shipped").assertValue(LocalDate.of(1992, 9, 10));
    }

    @Test
    public void testReadOnlyHeaderKeepsEachControlKind() {
        // ADR-0013: a read-only slice keeps the mapped control kind — the radio group,
        // the check box, and the date pickers are not flattened to plain text fields.
        ComboBoxElement.getByLabel(page, "Find order").filterAndSelectItem("50001", "50001");

        RadioButtonGroupElement.getByLabel(page, "Payment Type").assertValue("Cash");
        CheckboxElement.getByLabel(page, "Order Filled").assertChecked();

        assertThat(page.locator("#order-header vaadin-radio-group")).hasCount(1);
        assertThat(page.locator("#order-header vaadin-checkbox")).hasCount(1);
        assertThat(page.locator("#order-header vaadin-date-picker")).hasCount(2);
    }

    @Test
    public void testOrderIdIsReadOnly() {
        // Re-decided: the PK is sequence-assigned, never typed — shown read-only.
        assertThat(page.locator("#field-order-id")).hasAttribute("readonly", "");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testLayoutFidelityRendersEveryHeaderFieldInTheFormsOrder() {
        // Layout-fidelity gate (ADR-0012): every header item this slice renders appears
        // in the form's order (analyzer layoutPlan), never an invented order.
        List<String> expectedOrder = List.of(
                "field-order-id",
                "field-date-ordered",
                "field-customer-id",
                "field-customer-name",
                "field-sales-rep-id",
                "field-sales-rep-name",
                "field-date-shipped",
                "field-order-filled",
                "field-payment-type");

        // Scope to the mapped fields (FormLayout adds its own internal #layout child).
        Object renderedOrder = page.locator("#order-header > [id^='field-']")
                .evaluateAll("els => els.map(e => e.id)");

        assertEquals(expectedOrder, renderedOrder);
    }
}
