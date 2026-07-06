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

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * UI integration test for {@link OrderView} — the ORDERS tracer bullet (issue #85) driven
 * in a real browser (Playwright + DramaFinder) against a real Oracle. It exercises the two
 * dimensions the migration tracks (CONTEXT.md):
 * <ul>
 *   <li><strong>Behavioural parity</strong>: a quick-search term matching order id /
 *       customer / sales rep returns suggestions, and picking one loads that single order's
 *       header — the joined customer and rep names arriving with it.</li>
 *   <li><strong>Layout fidelity</strong> (the gate, ADR-0012/0013): every header item
 *       renders, in the form's order, as its mapped control kind in a read-only state — a
 *       DatePicker for a date, a RadioButtonGroup for the Cash/Credit radio, a Checkbox for
 *       the Y/N flag — not flattened to text.</li>
 * </ul>
 * Values come from the seed data in {@code db/summit-slice.sql} (order 50001: Unisports /
 * Magee / 1992-08-31 → 1992-09-10 / CASH / filled).
 */
public class OrderViewIT extends OracleSpringPlaywrightIT {

    @Override
    public String getView() {
        // getUrl() already ends in "/", so return the route without a leading slash to
        // avoid a "//orders" double slash that never resolves.
        return "orders";
    }

    @Test
    public void testTitle() {
        assertThat(page).hasTitle("Orders | Summit Migration POC");
    }

    @Test
    public void testQuickSearchByOrderIdReturnsSuggestion() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Quick search");
        search.setFilter("50001");
        search.assertItemCount(1);
    }

    @Test
    public void testQuickSearchByCustomerNameReturnsSuggestion() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Quick search");
        search.setFilter("Womansport");
        search.assertItemCount(1);
    }

    @Test
    public void testQuickSearchBySalesRepReturnsSuggestion() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Quick search");
        search.setFilter("Magee");
        search.assertItemCount(1);
    }

    @Test
    public void testSelectingSuggestionLoadsCustomerName() {
        loadOrder50001();

        // The customer name arrived on the same loadGraph read as the order (join, no N+1).
        TextFieldElement.getByLabel(page, "Customer Name").assertValue("Unisports");
    }

    @Test
    public void testSelectingSuggestionLoadsSalesRepName() {
        loadOrder50001();

        TextFieldElement.getByLabel(page, "Sales Rep Name").assertValue("Magee");
    }

    @Test
    public void testOrderIdIsReadOnly() {
        loadOrder50001();

        TextFieldElement orderId = TextFieldElement.getByLabel(page, "Order Id");
        orderId.assertValue("50001");
        // The PK is displayed read-only — never an editable/typed field (ledger #85).
        assertThat(orderId.getLocator()).hasAttribute("readonly", "");
    }

    @Test
    public void testPaymentTypeKeepsRadioControlKind() {
        loadOrder50001();

        // ADR-0013: the Radio Group stays a RadioButtonGroup in a read-only state, not a
        // TextField reading "CASH". Finding it via RadioButtonGroupElement proves the kind.
        RadioButtonGroupElement paymentType = RadioButtonGroupElement.getByLabel(page, "Payment Type");
        paymentType.assertValue("Cash");
        assertThat(paymentType.getLocator()).hasAttribute("readonly", "");
    }

    @Test
    public void testDatesKeepDatePickerControlKind() {
        loadOrder50001();

        DatePickerElement dateOrdered = DatePickerElement.getByLabel(page, "Date Ordered");
        dateOrdered.assertValue(LocalDate.of(1992, 8, 31));
        DatePickerElement.getByLabel(page, "Date Shipped").assertValue(LocalDate.of(1992, 9, 10));
    }

    @Test
    public void testOrderFilledKeepsCheckboxControlKind() {
        loadOrder50001();

        // Order 50001 is filled (order_filled = 'Y') — a read-only Checkbox, not text.
        CheckboxElement.getByLabel(page, "Order Filled").assertChecked();
    }

    @Test
    public void testHeaderRendersEveryFieldInFormOrder() {
        loadOrder50001();

        // Layout-fidelity gate (ADR-0012): every in-scope header item renders, in the form's
        // order derived from the spec layoutPlan. Assert the FormLayout's children by id.
        String[] expectedOrder = {
                "order-id", "date-ordered", "customer-id", "customer-name",
                "sales-rep-id", "sales-rep-name", "date-shipped", "order-filled", "payment-type"
        };
        // Select the field elements by tag (Playwright pierces shadow DOM, so ":scope > *"
        // would also pick up the FormLayout's internal shadow container div). hasCount
        // auto-waits for the async header render before the order assertions run.
        Locator fields = page.locator("#order-header")
                .locator("vaadin-text-field, vaadin-date-picker, vaadin-checkbox, vaadin-radio-group");
        assertThat(fields).hasCount(expectedOrder.length);
        for (int i = 0; i < expectedOrder.length; i++) {
            assertThat(fields.nth(i)).hasId(expectedOrder[i]);
        }
    }

    /** Quick-search order 50001 and pick its suggestion, loading the read-only header. */
    private void loadOrder50001() {
        ComboBoxElement search = ComboBoxElement.getByLabel(page, "Quick search");
        search.filterAndSelectItem("50001", "50001");
    }
}
