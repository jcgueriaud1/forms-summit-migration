package com.example.summit.presenter;

import com.example.summit.domain.Order;
import com.example.summit.domain.OrderSummary;
import com.example.summit.service.OrderService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link OrderPresenter} — the orchestration layer in isolation, with the
 * service mocked (prior art: {@code OrderPresenterTest}). It asserts the presenter is a
 * faithful delegator: search suggestions and the loaded header pass through untouched, and
 * a missing order stays empty. The join / SQL correctness is proven separately against a
 * real Oracle in {@code OrderRepositoryIT}.
 */
class OrderPresenterTest {

    private final OrderService service = mock(OrderService.class);
    private final OrderPresenter presenter = new OrderPresenter(service);

    @Test
    void searchReturnsServiceSuggestions() {
        List<OrderSummary> suggestions = List.of(
                new OrderSummary(50001, "Unisports", "Magee"),
                new OrderSummary(50003, "Unisports", null));
        when(service.search("Uni")).thenReturn(suggestions);

        assertThat(presenter.search("Uni")).isEqualTo(suggestions);
    }

    @Test
    void loadReturnsHeaderWithJoinedNames() {
        Order order = new Order(50001, 201, "Unisports",
                LocalDate.of(1992, 8, 31), LocalDate.of(1992, 9, 10),
                1L, "Magee", "CASH", true);
        when(service.loadGraph(50001)).thenReturn(Optional.of(order));

        assertThat(presenter.load(50001)).contains(order);
    }

    @Test
    void loadIsEmptyWhenOrderMissing() {
        when(service.loadGraph(99999)).thenReturn(Optional.empty());

        assertThat(presenter.load(99999)).isEmpty();
    }
}
