package com.example.summit.presenter;

import com.example.summit.domain.Order;
import com.example.summit.domain.OrderSearchResult;
import com.example.summit.service.OrderService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Presenter unit test — the layer is pure orchestration, so the Service is mocked and
 * no Spring context or database is involved (mirrors the scaffold's
 * {@code ServerInfoFormulaTest} purity). It pins the one piece of logic the presenter
 * owns: the blank-term guard that keeps an empty type-ahead off the database.
 */
class OrderPresenterTest {

    private final OrderService service = mock(OrderService.class);
    private final OrderPresenter presenter = new OrderPresenter(service);

    @Test
    void searchDelegatesToServiceForANonBlankTerm() {
        List<OrderSearchResult> hits = List.of(new OrderSearchResult(50001L, "Unisports", "Colin Magee"));
        when(service.search("uni")).thenReturn(hits);

        assertSame(hits, presenter.search("uni"));
        verify(service).search("uni");
    }

    @Test
    void searchShortCircuitsBlankTermsWithoutTouchingTheService() {
        assertTrue(presenter.search(null).isEmpty());
        assertTrue(presenter.search("").isEmpty());
        assertTrue(presenter.search("   ").isEmpty());
        verifyNoInteractions(service);
    }

    @Test
    void loadDelegatesToServiceByOrderId() {
        Order order = new Order(50001L, 201L, "Unisports",
                LocalDate.of(1992, 8, 31), LocalDate.of(1992, 9, 10),
                1L, "Colin Magee", "CASH", true);
        when(service.load(50001L)).thenReturn(Optional.of(order));

        assertEquals(Optional.of(order), presenter.load(50001L));
        verify(service).load(50001L);
    }

    @Test
    void loadReturnsEmptyWhenTheOrderIsGone() {
        when(service.load(99999L)).thenReturn(Optional.empty());

        assertTrue(presenter.load(99999L).isEmpty());
        verify(service).load(99999L);
        verify(service, never()).search(org.mockito.ArgumentMatchers.anyString());
    }
}
