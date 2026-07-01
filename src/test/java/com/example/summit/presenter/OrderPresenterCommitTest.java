package com.example.summit.presenter;

import com.example.summit.domain.CommitResult;
import com.example.summit.domain.Order;
import com.example.summit.domain.OrderLine;
import com.example.summit.domain.OrderSearchCriteria;
import com.example.summit.domain.OrderSummary;
import com.example.summit.service.OrderService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Pure unit test for the presenter's whole-graph commit orchestration (issue #19) — no Spring, no
 * DB, no browser. A recording stub {@link OrderService} stands in for the transactional save: the
 * test drives the presenter's contract around it — it assembles the working header + working lines
 * (carrying the load {@code ORA_ROWSCN}) into the order handed to {@link OrderService#save}, and on a
 * {@link CommitResult.Committed} it re-seeds the working state from a fresh load (the moved scn) so a
 * follow-up commit builds on the persisted state; on a {@link CommitResult.Conflict} it leaves the
 * clerk's edits untouched. The Service is the authoritative gate — the presenter only orchestrates.
 */
class OrderPresenterCommitTest {

    private static final OrderLine BOOTS = new OrderLine(1, 41010, "Ski Boots", new BigDecimal("50.00"), 5, 5);

    /** The loaded order (scn 7) the working session starts from. */
    private static final Order LOADED = new Order(
            50001, 201, "Unisports", LocalDate.of(1992, 8, 31), LocalDate.of(1992, 9, 10),
            1L, "Magee", "CASH", true, List.of(BOOTS), 7L);

    /** What a post-commit reload returns (scn 9) — proves the working state is re-seeded from truth. */
    private static final Order RELOADED = new Order(
            50001, 201, "Unisports", LocalDate.of(1992, 8, 31), LocalDate.of(1992, 9, 10),
            1L, "Magee", "CASH", true, List.of(BOOTS), 9L);

    /** Records the order handed to save and returns scripted load / save results. */
    private static final class StubService extends OrderService {
        Order savedOrder;
        Order loadResult = LOADED;
        CommitResult saveResult;

        StubService() {
            super(null);
        }

        @Override
        public List<OrderSummary> search(OrderSearchCriteria criteria) {
            return List.of(new OrderSummary(LOADED.id(), LOADED.customerName(), LOADED.salesRepName(), LOADED.dateOrdered()));
        }

        @Override
        public Optional<Order> load(long orderId) {
            return Optional.of(loadResult);
        }

        @Override
        public CommitResult save(Order order) {
            this.savedOrder = order;
            return saveResult;
        }
    }

    private static OrderPresenter loadedPresenter(StubService service) {
        OrderPresenter presenter = new OrderPresenter(service, null, null);
        presenter.search(new OrderSearchCriteria("Unisports")); // seeds the working state from LOADED
        return presenter;
    }

    @Test
    void commitAssemblesTheWorkingGraphCarryingTheLoadScn() {
        StubService service = new StubService();
        service.saveResult = new CommitResult.Committed(50001);
        OrderPresenter presenter = loadedPresenter(service);

        // The clerk edits the header and adds a line, then commits.
        presenter.editableHeader().setPaymentType("CREDIT");
        presenter.addLine();
        presenter.commit();

        // The order handed to save reflects the working edits and carries the scn read at load (7).
        assertEquals("CREDIT", service.savedOrder.paymentType());
        assertEquals(List.of(1L, 2L), service.savedOrder.lines().stream().map(OrderLine::itemId).toList());
        assertEquals(7L, service.savedOrder.rowScn());
    }

    @Test
    void aCommittedSaveReseedsTheWorkingStateFromAFreshLoad() {
        StubService service = new StubService();
        service.saveResult = new CommitResult.Committed(50001);
        OrderPresenter presenter = loadedPresenter(service);
        assertEquals(7L, presenter.editableHeader().getRowScn()); // seeded from LOADED

        service.loadResult = RELOADED; // the post-commit reload returns the moved scn
        CommitResult result = presenter.commit();

        assertInstanceOf(CommitResult.Committed.class, result);
        // The working header is re-seeded from the reloaded order, so a follow-up commit uses scn 9.
        assertEquals(9L, presenter.editableHeader().getRowScn());
    }

    @Test
    void aConflictLeavesTheWorkingStateUntouched() {
        StubService service = new StubService();
        service.saveResult = new CommitResult.Conflict();
        OrderPresenter presenter = loadedPresenter(service);

        service.loadResult = RELOADED; // must NOT be consulted on a conflict
        CommitResult result = presenter.commit();

        assertInstanceOf(CommitResult.Conflict.class, result);
        // No re-seed: the clerk's session still holds the originally-loaded scn to reconcile from.
        assertEquals(7L, presenter.editableHeader().getRowScn());
    }

    @Test
    void committingWithNothingLoadedIsRejected() {
        OrderPresenter presenter = new OrderPresenter(new StubService(), null, null);

        CommitResult result = presenter.commit();

        assertInstanceOf(CommitResult.Rejected.class, result);
    }
}
