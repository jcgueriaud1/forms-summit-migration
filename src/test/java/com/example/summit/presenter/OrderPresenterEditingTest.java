package com.example.summit.presenter;

import com.example.summit.domain.Order;
import com.example.summit.domain.OrderLine;
import com.example.summit.domain.OrderSearchCriteria;
import com.example.summit.domain.OrderSummary;
import com.example.summit.domain.ProductDefaults;
import com.example.summit.service.CustomerService;
import com.example.summit.service.OrderService;
import com.example.summit.service.ProductService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit test for the presenter's in-memory line editing (issue #45) — no Spring, no DB, no
 * browser. A stub {@link OrderService} loads one fixed order; the test drives the working-set
 * orchestration the editable grid relies on: committing a search seeds the working lines, adding a
 * line assigns {@code MAX(item_id)+1} over the <em>current</em> working set (including lines just
 * added), duplicating clones a line through the {@code KEY-DUPREC} clear-list, removing drops a line,
 * and {@link OrderPresenter#currentTotal()} re-sums the edited lines live — the recompute path the
 * view's Binder listeners call into (ADR-0002, no Signals).
 */
class OrderPresenterEditingTest {

    private static final OrderLine BOOTS = new OrderLine(1, 41010, "Ski Boots", new BigDecimal("50.00"), 5, 5);
    private static final OrderLine POLE = new OrderLine(2, 20108, "Ski Pole", new BigDecimal("13.00"), 27, 27);

    private static final Order ORDER = new Order(
            50001, 201, "Unisports", LocalDate.of(1992, 8, 31), LocalDate.of(1992, 9, 10),
            1L, "Magee", "CASH", true, 0L, List.of(BOOTS, POLE));

    /** The one product the stub LOV knows — id 30421, "Pro Ski Boot", suggested price 1000.00. */
    private static final long PRO_BOOT_ID = 30421;

    /** A presenter wired to a stub service that always returns {@link #ORDER}, loaded and positioned. */
    private static OrderPresenter loadedPresenter() {
        OrderService orderService = new OrderService(null) {
            @Override
            public List<OrderSummary> search(OrderSearchCriteria criteria) {
                return List.of(new OrderSummary(
                        ORDER.id(), ORDER.customerName(), ORDER.salesRepName(), ORDER.dateOrdered()));
            }

            @Override
            public Optional<Order> load(long orderId) {
                return Optional.of(ORDER);
            }
        };
        // A stub product service that knows exactly one product, so the defaulter's found / not-found
        // branches are both exercisable without a database.
        ProductService productService = new ProductService(null) {
            @Override
            public Optional<ProductDefaults> findProductDefaults(long productId) {
                return productId == PRO_BOOT_ID
                        ? Optional.of(new ProductDefaults("Pro Ski Boot", new BigDecimal("1000.00")))
                        : Optional.empty();
            }
        };
        // The header lookups (issue #47) are not exercised here — a bare CustomerService suffices.
        CustomerService customerService = new CustomerService(null);
        OrderPresenter presenter = new OrderPresenter(orderService, productService, customerService);
        presenter.select("Unisports", ORDER.id());
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
    void duplicateLineClonesTheClearListAndAssignsTheNextItemId() {
        OrderPresenter presenter = loadedPresenter();
        EditableLine boots = presenter.editableLines().get(0);

        EditableLine copy = presenter.duplicateLine(boots);

        // The copy gets the next per-order id (3) and carries the quantities, but the KEY-DUPREC
        // fields (product/description/price) are cleared so it inserts rather than colliding.
        assertEquals(3L, copy.getItemId());
        assertEquals(5L, copy.getQuantity());
        assertEquals(5L, copy.getQuantityShipped());
        assertEquals(0L, copy.getProductId());
        assertNull(copy.getPrice());
        assertEquals(3, presenter.editableLines().size());
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

    // ---- Product LOV + WHEN-VALIDATE-ITEM defaulter (issue #46) --------------------------------

    @Test
    void applyProductDefaultsReDefaultsDescriptionAndPriceFromTheProduct() {
        OrderPresenter presenter = loadedPresenter();
        EditableLine line = presenter.addLine(); // a blank, unproducted new line (item 3)

        boolean applied = presenter.applyProductDefaults(line, PRO_BOOT_ID);

        // The defaulter writes the id and re-defaults the description (product name) and price
        // (suggested wholesale) onto the line — fired on the product_id change, LOV pick or direct entry.
        assertTrue(applied);
        assertEquals(PRO_BOOT_ID, line.getProductId());
        assertEquals("Pro Ski Boot", line.getDescription());
        assertEquals(0, new BigDecimal("1000.00").compareTo(line.getPrice()));
    }

    @Test
    void applyProductDefaultsReDefaultsOnAnyProductChange() {
        OrderPresenter presenter = loadedPresenter();
        EditableLine boots = presenter.editableLines().get(0); // loaded as 41010 / "Ski Boots" / 50.00

        // Changing the product re-defaults the line again — the defaulter is not a one-shot on new lines.
        presenter.applyProductDefaults(boots, PRO_BOOT_ID);

        assertEquals(PRO_BOOT_ID, boots.getProductId());
        assertEquals("Pro Ski Boot", boots.getDescription());
        assertEquals(0, new BigDecimal("1000.00").compareTo(boots.getPrice()));
    }

    @Test
    void applyProductDefaultsRejectsAnUnknownProductAndLeavesTheLineUntouched() {
        OrderPresenter presenter = loadedPresenter();
        EditableLine boots = presenter.editableLines().get(0);

        boolean applied = presenter.applyProductDefaults(boots, 999999);

        // The NO_DATA_FOUND case: rejected (false) and the line keeps its original product/description/price.
        assertFalse(applied);
        assertEquals(41010L, boots.getProductId());
        assertEquals("Ski Boots", boots.getDescription());
        assertEquals(0, new BigDecimal("50.00").compareTo(boots.getPrice()));
    }

    @Test
    void resetClearsTheWorkingSet() {
        OrderPresenter presenter = loadedPresenter();
        assertFalse(presenter.editableLines().isEmpty());

        presenter.reset();

        assertTrue(presenter.editableLines().isEmpty());
        assertEquals(0, BigDecimal.ZERO.compareTo(presenter.currentTotal()));
    }
}
