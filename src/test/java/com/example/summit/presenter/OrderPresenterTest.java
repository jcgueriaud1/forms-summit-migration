package com.example.summit.presenter;

import com.example.summit.domain.Order;
import com.example.summit.domain.OrderSummary;
import com.example.summit.domain.PaymentType;
import com.example.summit.service.OrderService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Presenter unit test with the service mocked — the presenter's public interface is
 * pure orchestration in this slice (search, load), so we assert it delegates and
 * returns what the service gives, no Spring context or DB (prior art: the pure
 * {@code ServerInfoFormulaTest}). Behavioural depth (totals, defaults, commit) arrives
 * in later slices and is tested there.
 */
class OrderPresenterTest {

    @Test
    void searchDelegatesToServiceAndReturnsSuggestions() {
        OrderService service = mock(OrderService.class);
        List<OrderSummary> suggestions = List.of(
                new OrderSummary(50001, "Unisports", "Magee"),
                new OrderSummary(50003, "Unisports", "Magee"));
        when(service.search("uni")).thenReturn(suggestions);

        OrderPresenter presenter = new OrderPresenter(service);

        assertSame(suggestions, presenter.search("uni"));
    }

    @Test
    void loadDelegatesToServiceForThePickedOrder() {
        OrderService service = mock(OrderService.class);
        Order order = new Order(50001, 201, "Unisports",
                LocalDate.of(1992, 8, 31), LocalDate.of(1992, 9, 10),
                1, "Magee", PaymentType.CASH, true);
        when(service.load(50001)).thenReturn(Optional.of(order));

        OrderPresenter presenter = new OrderPresenter(service);
        Optional<Order> loaded = presenter.load(50001);

        assertTrue(loaded.isPresent());
        assertEquals(order, loaded.get());
    }

    @Test
    void loadReturnsEmptyWhenOrderIsGone() {
        OrderService service = mock(OrderService.class);
        when(service.load(99999)).thenReturn(Optional.empty());

        OrderPresenter presenter = new OrderPresenter(service);

        assertTrue(presenter.load(99999).isEmpty());
    }
}
