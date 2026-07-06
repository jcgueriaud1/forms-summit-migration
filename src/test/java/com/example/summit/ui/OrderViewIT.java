package com.example.summit.ui;

import com.example.summit.it.support.OracleSpringPlaywrightIT;
import com.microsoft.playwright.Locator;
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
 * UI integration test for {@link OrderView} — the quick search &rarr; read-only header
 * tracer bullet (issue #85), driven in a real browser (Playwright + DramaFinder)
 * against a seeded Oracle. Asserts each covered {@code migrated-as} behaviour
 * end-to-end plus the layout-fidelity gate (ADR-0012/0013).
 */
public class OrderViewIT extends OracleSpringPlaywrightIT {

    /** The header fields in the form's field order (spec {@code layoutPlan}, ADR-0012). */
    private static final List<String> EXPECTED_FIELD_ORDER = List.of(
            "order-id", "date-ordered", "customer-id", "customer-name",
            "sales-rep-id", "sales-rep-name", "date-shipped", "order-filled", "payment-type");

    @Override
    public String getView() {
        return "orders";
    }

    private ComboBoxElement quickSearch() {
        return ComboBoxElement.getByLabel(page, "Search orders");
    }

    /** Picks a suggestion whose label contains {@code text} and loads its header. */
    private void loadOrder(String text) {
        quickSearch().filterAndSelectItem(text, text);
    }

    @Test
    public void testTitle() {
        assertThat(page).hasTitle("Orders | Summit Migration POC");
    }

    @Test
    public void quickSearchByOrderIdSuggestsTheMatch() {
        quickSearch().setFilter("50002");
        quickSearch().assertItemCount(1);
    }

    @Test
    public void quickSearchByCustomerNameSuggestsAllThatCustomersOrders() {
        // Customer 201 (Unisports) has orders 50001 and 50003.
        quickSearch().setFilter("Unisports");
        quickSearch().assertItemCount(2);
    }

    @Test
    public void quickSearchBySalesRepSuggestsTheMatch() {
        quickSearch().setFilter("Giljum");
        quickSearch().assertItemCount(1);
    }

    @Test
    public void selectingASuggestionLoadsThatSingleOrdersHeader() {
        loadOrder("50001");

        // A single order's header (one Order Id), not a list.
        TextFieldElement orderId = TextFieldElement.getByLabel(page, "Order Id");
        orderId.assertValue("50001");
    }

    @Test
    public void customerAndSalesRepNamesArriveNextToTheirIds() {
        loadOrder("50001");

        TextFieldElement.getByLabel(page, "Customer Id").assertValue("201");
        TextFieldElement.getByLabel(page, "Customer Name").assertValue("Unisports");
        TextFieldElement.getByLabel(page, "Sales Rep Id").assertValue("1");
        TextFieldElement.getByLabel(page, "Sales Rep Name").assertValue("Magee");
    }

    @Test
    public void theOrderIdIsDisplayedReadOnly() {
        loadOrder("50001");

        TextFieldElement orderId = TextFieldElement.getByLabel(page, "Order Id");
        assertThat(orderId.getLocator()).hasAttribute("readonly", "");
    }

    @Test
    public void readOnlyHeaderKeepsEachControlKind() {
        // 50002: CREDIT payment, not filled, ordered 1992-09-01, no ship date.
        loadOrder("50002");

        // Payment type stays a radio group (not flattened to text), showing Credit.
        RadioButtonGroupElement paymentType = RadioButtonGroupElement.getByLabel(page, "Payment Type");
        paymentType.assertVisible();
        paymentType.assertValue("Credit");

        // Order filled stays a checkbox.
        CheckboxElement orderFilled = CheckboxElement.getByLabel(page, "Order Filled");
        orderFilled.assertVisible();
        orderFilled.assertNotChecked();

        // Dates stay date pickers.
        DatePickerElement dateOrdered = DatePickerElement.getByLabel(page, "Date Ordered");
        dateOrdered.assertVisible();
        dateOrdered.assertValue(LocalDate.of(1992, 9, 1));
    }

    @Test
    public void checkedOrderFilledAndCashPaymentRenderInReadOnlyState() {
        // 50001: CASH payment, filled, ordered/shipped dates present.
        loadOrder("50001");

        RadioButtonGroupElement.getByLabel(page, "Payment Type").assertValue("Cash");
        CheckboxElement.getByLabel(page, "Order Filled").assertChecked();
        DatePickerElement.getByLabel(page, "Date Shipped").assertValue(LocalDate.of(1992, 9, 10));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void layoutFidelityRendersEveryHeaderFieldInFormOrder() {
        loadOrder("50001");

        // Wait for the (initially-invisible) header to load before the one-shot evaluate.
        assertThat(page.locator("#order-header-form")).isVisible();

        Locator fields = page.locator("#order-header-form > *");
        List<String> actualOrder = (List<String>) fields.evaluateAll(
                "els => els.map(e => e.getAttribute('data-field')).filter(Boolean)");

        assertEquals(EXPECTED_FIELD_ORDER, actualOrder);
    }
}
