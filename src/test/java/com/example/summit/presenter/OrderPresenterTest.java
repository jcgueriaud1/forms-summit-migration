package com.example.summit.presenter;

import com.example.summit.domain.Order;
import com.example.summit.domain.OrderSearchCriteria;
import com.example.summit.domain.OrderSummary;
import com.example.summit.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link OrderPresenter}'s result-set position — the record-scrollbar
 * replacement (issue #42). The acceptance criterion "position state lives in the
 * presenter" is exactly what makes this testable without a browser or a database: the
 * {@link OrderService} is mocked, so the prev/next stepping, the "record N of M" counts,
 * and the end-of-set guards are exercised in isolation.
 */
class OrderPresenterTest {

    // A three-order result set the pager walks, ordered as findByCriteria returns them.
    private static final OrderSummary ORDER_A = summary(50001);
    private static final OrderSummary ORDER_B = summary(50002);
    private static final OrderSummary ORDER_C = summary(50003);
    private static final List<OrderSummary> RESULT_SET = List.of(ORDER_A, ORDER_B, ORDER_C);

    private OrderService orderService;
    private OrderPresenter presenter;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        presenter = new OrderPresenter(orderService);
        when(orderService.search(any(OrderSearchCriteria.class))).thenReturn(RESULT_SET);
        when(orderService.load(anyLong())).thenAnswer(call -> Optional.of(order(call.getArgument(0))));
    }

    @Test
    void selectFixesTheResultSetAndPositionsAtThePickedOrder() {
        Optional<Order> loaded = presenter.select("term", 50002L);

        assertEquals(50002L, loaded.orElseThrow().id());
        // Record 2 of 3 — a middle record, so both ends are open.
        assertEquals(2, presenter.currentRecord());
        assertEquals(3, presenter.resultCount());
        assertTrue(presenter.hasPrevious());
        assertTrue(presenter.hasNext());
    }

    @Test
    void nextAndPreviousStepThroughTheResultSetLoadingEachOrder() {
        presenter.select("term", 50001L);

        assertEquals(50002L, presenter.next().orElseThrow().id());
        assertEquals(2, presenter.currentRecord());
        assertEquals(50003L, presenter.next().orElseThrow().id());
        assertEquals(3, presenter.currentRecord());
        assertEquals(50002L, presenter.previous().orElseThrow().id());
        assertEquals(2, presenter.currentRecord());
    }

    @Test
    void previousIsDisabledAndInertAtTheFirstRecord() {
        presenter.select("term", 50001L);

        assertFalse(presenter.hasPrevious());
        assertTrue(presenter.previous().isEmpty());
        // Stepping off the front does not move the position.
        assertEquals(1, presenter.currentRecord());
    }

    @Test
    void nextIsDisabledAndInertAtTheLastRecord() {
        presenter.select("term", 50003L);

        assertFalse(presenter.hasNext());
        assertTrue(presenter.next().isEmpty());
        assertEquals(3, presenter.currentRecord());
    }

    @Test
    void currentSummaryTracksThePagedRecordForTheSearchField() {
        presenter.select("term", 50001L);
        assertEquals(ORDER_A, presenter.currentSummary().orElseThrow());

        presenter.next();
        assertEquals(ORDER_B, presenter.currentSummary().orElseThrow());
    }

    @Test
    void resetClearsThePositionSoThePagerHasNothingToWalk() {
        presenter.select("term", 50002L);
        presenter.reset();

        assertEquals(0, presenter.currentRecord());
        assertEquals(0, presenter.resultCount());
        assertFalse(presenter.hasPrevious());
        assertFalse(presenter.hasNext());
        assertTrue(presenter.currentSummary().isEmpty());
    }

    @Test
    void plainLoadDoesNotEstablishAPagerPosition() {
        // load() is the bare single-order path — no search, so nothing for the pager to walk.
        presenter.load(50002L);

        assertEquals(0, presenter.resultCount());
        assertEquals(0, presenter.currentRecord());
        verify(orderService, never()).search(any());
    }

    @Test
    void suggestDoesNotMutateTheWalkedResultSet() {
        // Typing in the autocomplete must not disturb a committed pager position.
        presenter.select("term", 50002L);
        presenter.suggest("unrelated");

        assertEquals(2, presenter.currentRecord());
        assertEquals(3, presenter.resultCount());
    }

    private static OrderSummary summary(long id) {
        return new OrderSummary(id, "Customer " + id, "Rep", LocalDate.of(1992, 8, 31));
    }

    private static Order order(long id) {
        return new Order(id, 201, "Customer " + id, LocalDate.of(1992, 8, 31), null,
                1L, "Rep", "CASH", true, List.of());
    }
}
