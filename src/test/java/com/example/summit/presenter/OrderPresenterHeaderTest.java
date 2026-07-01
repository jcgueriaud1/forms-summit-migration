package com.example.summit.presenter;

import com.example.summit.domain.Customer;
import com.example.summit.domain.Order;
import com.example.summit.domain.OrderSearchCriteria;
import com.example.summit.domain.OrderSummary;
import com.example.summit.service.CustomerService;
import com.example.summit.service.OrderService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit test for the presenter's editable-header orchestration (issue #18) — no Spring, no DB,
 * no browser. Stub {@link OrderService} / {@link CustomerService} stand in for the loads; the test
 * drives the two {@code S_ORD} behaviours the header form calls into: the
 * {@code WHEN-VALIDATE-ITEM} customer-id defaulter (a known id re-defaults name + sales rep onto
 * the working header; an unknown id is the {@code NO_DATA_FOUND} reject that leaves it untouched)
 * and the {@code WHEN-RADIO-CHANGED} credit guard ({@link OrderPresenter#paymentAllowed} reasons
 * over the working customer's credit rating through the domain rule).
 */
class OrderPresenterHeaderTest {

    private static final Customer UNISPORTS = new Customer(201, "Unisports", "EXCELLENT", 1L, "Magee");
    private static final Customer WOMANSPORT = new Customer(202, "Womansport", "POOR", 2L, "Giljum");

    private static final Order ORDER = new Order(
            50001, 201, "Unisports", LocalDate.of(1992, 8, 31), LocalDate.of(1992, 9, 10),
            1L, "Magee", "CASH", true, List.of(), 1L);

    /** A presenter with the order loaded (working header seeded) and a two-customer master. */
    private static OrderPresenter loadedPresenter() {
        OrderService orders = new OrderService(null) {
            @Override
            public List<OrderSummary> search(OrderSearchCriteria criteria) {
                return List.of(new OrderSummary(ORDER.id(), ORDER.customerName(), ORDER.salesRepName(), ORDER.dateOrdered()));
            }

            @Override
            public Optional<Order> load(long orderId) {
                return Optional.of(ORDER);
            }
        };
        CustomerService customers = new CustomerService(null) {
            @Override
            public Optional<Customer> findById(long customerId) {
                if (customerId == 201) {
                    return Optional.of(UNISPORTS);
                }
                if (customerId == 202) {
                    return Optional.of(WOMANSPORT);
                }
                return Optional.empty();
            }
        };
        OrderPresenter presenter = new OrderPresenter(orders, null, customers);
        presenter.search(new OrderSearchCriteria("Unisports"));
        return presenter;
    }

    @Test
    void loadingAnOrderSeedsTheWorkingHeader() {
        EditableHeader header = loadedPresenter().editableHeader();

        assertEquals(50001L, header.getId());
        assertEquals(201L, header.getCustomerId());
        assertEquals("Unisports", header.getCustomerName());
        assertEquals("CASH", header.getPaymentType());
    }

    @Test
    void applyingAKnownCustomerReDefaultsNameAndSalesRep() {
        OrderPresenter presenter = loadedPresenter();

        Optional<Customer> applied = presenter.applyCustomerDefaults(202);

        assertTrue(applied.isPresent());
        EditableHeader header = presenter.editableHeader();
        assertEquals(202L, header.getCustomerId());
        assertEquals("Womansport", header.getCustomerName());
        assertEquals(2L, header.getSalesRepId());
        assertEquals("Giljum", header.getSalesRepName());
    }

    @Test
    void anUnknownCustomerIdIsRejectedAndLeavesTheHeaderUntouched() {
        OrderPresenter presenter = loadedPresenter();

        Optional<Customer> applied = presenter.applyCustomerDefaults(99999);

        // NO_DATA_FOUND: empty result, and the header keeps its loaded customer (nothing
        // half-applied) so the caller can reject the invalid id.
        assertTrue(applied.isEmpty());
        EditableHeader header = presenter.editableHeader();
        assertEquals(201L, header.getCustomerId());
        assertEquals("Unisports", header.getCustomerName());
        assertEquals("Magee", header.getSalesRepName());
    }

    @Test
    void creditIsAllowedForTheLoadedExcellentCustomer() {
        // The loaded order's customer (201) is EXCELLENT — CREDIT is allowed without any edit.
        assertTrue(loadedPresenter().paymentAllowed("CREDIT"));
    }

    @Test
    void creditIsRefusedAfterSwitchingToAPoorCustomer() {
        OrderPresenter presenter = loadedPresenter();
        // Switch the working header to the POOR customer (202) via the defaulter...
        presenter.applyCustomerDefaults(202);

        // ...now a CREDIT selection is refused: the guard reasons over the working customer's rating.
        assertFalse(presenter.paymentAllowed("CREDIT"));
        // CASH stays allowed regardless.
        assertTrue(presenter.paymentAllowed("CASH"));
    }
}
