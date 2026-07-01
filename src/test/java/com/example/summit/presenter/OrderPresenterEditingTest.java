package com.example.summit.presenter;

import com.example.summit.domain.Order;
import com.example.summit.domain.OrderLine;
import com.example.summit.domain.OrderSearchCriteria;
import com.example.summit.domain.OrderSummary;
import com.example.summit.service.OrderService;
import com.example.summit.service.ProductService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit test for the presenter's in-memory line editing (issue #16) — no Spring, no DB, no
 * browser. A stub {@link OrderService} loads one fixed order; the test then drives the working-set
 * orchestration the editable grid relies on: a fresh load seeds the working lines, adding a line
 * assigns {@code MAX(item_id)+1} over the <em>current</em> working set (including lines just added),
 * removing drops a line, and {@link OrderPresenter#currentTotal()} re-sums the edited lines live —
 * the recompute path the view's Binder listeners call into (ADR-0002, no Signals).
 */
class OrderPresenterEditingTest {

    private static final OrderLine BOOTS = new OrderLine(1, 41010, "Ski Boots", new BigDecimal("50.00"), 5, 5);
    private static final OrderLine POLE = new OrderLine(2, 20108, "Ski Pole", new BigDecimal("13.00"), 27, 27);

    private static final Order ORDER = new Order(
            50001, 201, "Unisports", LocalDate.of(1992, 8, 31), LocalDate.of(1992, 9, 10),
            1L, "Magee", "CASH", true, List.of(BOOTS, POLE), 1L);

    /** A presenter wired to a stub service that always returns {@link #ORDER}, loaded and positioned. */
    private static OrderPresenter loadedPresenter() {
        OrderService service = new OrderService(null) {
            @Override
            public List<OrderSummary> search(OrderSearchCriteria criteria) {
                return List.of(new OrderSummary(ORDER.id(), ORDER.customerName(), ORDER.salesRepName(), ORDER.dateOrdered()));
            }

            @Override
            public Optional<Order> load(long orderId) {
                return Optional.of(ORDER);
            }
        };
        // This slice exercises only the line working-set; the product LOV/defaulter (issue #17) and
        // the customer lookup (issue #18) have their own tests, so null Product/Customer services are
        // enough — loading seeds the working header from the order itself, none of these reach them.
        OrderPresenter presenter = new OrderPresenter(service, null, null);
        presenter.search(new OrderSearchCriteria("Unisports"));
        return presenter;
    }

    @Test
    void loadingAnOrderSeedsTheWorkingLines() {
        OrderPresenter presenter = loadedPresenter();

        assertEquals(List.of(1L, 2L),
                presenter.editableLines().stream().map(EditableLine::getItemId).toList());
    }

    @Test
    void addLineAssignsMaxItemIdPlusOne() {
        OrderPresenter presenter = loadedPresenter();

        EditableLine added = presenter.addLine();

        // Lines are 1 and 2 -> the new line is 3 (per-order MAX+1).
        assertEquals(3L, added.getItemId());
        assertEquals(3, presenter.editableLines().size());
    }

    @Test
    void successiveAddsKeepClimbingOverTheWorkingSet() {
        OrderPresenter presenter = loadedPresenter();

        // The second add must see the first added line (3) and become 4 — MAX over the working set,
        // not over the originally-loaded lines.
        assertEquals(3L, presenter.addLine().getItemId());
        assertEquals(4L, presenter.addLine().getItemId());
    }

    @Test
    void removeLineDropsItFromTheWorkingSet() {
        OrderPresenter presenter = loadedPresenter();
        EditableLine pole = presenter.editableLines().get(1);

        presenter.removeLine(pole);

        assertEquals(List.of(1L),
                presenter.editableLines().stream().map(EditableLine::getItemId).toList());
    }

    @Test
    void currentTotalSumsTheWorkingLinesLive() {
        OrderPresenter presenter = loadedPresenter();
        // 5 * 50.00 + 27 * 13.00 = 601.00 on load.
        assertEquals(0, new BigDecimal("601.00").compareTo(presenter.currentTotal()));

        // Editing the boots' shipped quantity recomputes the total at once (no re-load).
        presenter.editableLines().get(0).setQuantityShipped(6); // 6 * 50.00 = 300.00
        assertEquals(0, new BigDecimal("651.00").compareTo(presenter.currentTotal()));

        // Adding a blank (unpriced) line contributes zero until it is filled.
        presenter.addLine();
        assertEquals(0, new BigDecimal("651.00").compareTo(presenter.currentTotal()));

        // Removing a line drops its contribution from the live total.
        presenter.removeLine(presenter.editableLines().get(1)); // drop the pole (27 * 13.00 = 351.00)
        assertEquals(0, new BigDecimal("300.00").compareTo(presenter.currentTotal()));
    }

    @Test
    void clearResetsTheWorkingSet() {
        OrderPresenter presenter = loadedPresenter();
        assertFalse(presenter.editableLines().isEmpty());

        presenter.clear();

        assertTrue(presenter.editableLines().isEmpty());
        assertEquals(0, BigDecimal.ZERO.compareTo(presenter.currentTotal()));
    }
}
