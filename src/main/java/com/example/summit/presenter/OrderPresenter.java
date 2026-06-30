package com.example.summit.presenter;

import com.example.summit.domain.Order;
import com.example.summit.domain.OrderSearchCriteria;
import com.example.summit.domain.OrderSummary;
import com.example.summit.service.OrderService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Presenter layer — owns <em>orchestration</em>. It turns the clerk's typed term into
 * an {@link OrderSearchCriteria} for the autocomplete, loads the order the clerk picks,
 * and owns the <strong>cursor over the search result set</strong> that the prev/next
 * pager steps through (PRD #8, #13). It calls the Service; it holds no SQL and no
 * business formula.
 *
 * <h2>Pager state (the Forms record scrollbar)</h2>
 * Picking a suggestion {@link #open(OrderSummary) opens} the result set for that quick
 * search and positions the cursor at the picked order; {@link #next()} / {@link #previous()}
 * step through the matching orders one at a time, reusing the single-order load path. The
 * position ({@link #recordNumber()} of {@link #recordCount()}) lives here, not in the view
 * (issue #13) — so the view stays a thin renderer of presenter state.
 *
 * <p><b>Scope.</b> {@code prototype}: each {@link com.example.summit.ui.OrderView} (recreated on
 * every navigation) gets its own presenter, so the cursor's lifetime matches the view's exactly —
 * a fresh view starts from a clean cursor. A default-singleton {@code @Component} would instead let
 * two clerks browsing at once corrupt each other's position.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class OrderPresenter {

    private final OrderService orderService;

    /** The quick-search result set the clerk is stepping through (the Forms query result). */
    private List<OrderSummary> resultSet = List.of();

    /** Zero-based cursor into {@link #resultSet}; {@code -1} when nothing is loaded. */
    private int position = -1;

    public OrderPresenter(OrderService orderService) {
        this.orderService = orderService;
    }

    /** Quick-search suggestions for the term the clerk has typed so far. */
    public List<OrderSummary> suggest(String quickTerm) {
        return orderService.search(new OrderSearchCriteria(quickTerm));
    }

    /**
     * Opens the result set for {@code quickTerm} and positions the cursor at the {@code picked}
     * suggestion, loading that order. This is the entry point the pager then steps through: the
     * result set is every order matching the same quick term the clerk searched, so prev/next
     * walk the clerk's query exactly as Forms' record scrollbar did.
     */
    public Optional<Order> open(String quickTerm, OrderSummary picked) {
        this.resultSet = orderService.search(new OrderSearchCriteria(quickTerm));
        int index = indexOf(picked.id());
        if (index < 0) {
            // Defensive: the picked order isn't in the re-queried set — keep it a one-record
            // set so the pager still reports "record 1 of 1" rather than losing the order.
            this.resultSet = List.of(picked);
            index = 0;
        }
        this.position = index;
        return loadCurrent();
    }

    /** Steps to the next order in the result set, or {@link Optional#empty()} at the last. */
    public Optional<Order> next() {
        if (!hasNext()) {
            return Optional.empty();
        }
        position++;
        return loadCurrent();
    }

    /** Steps to the previous order in the result set, or {@link Optional#empty()} at the first. */
    public Optional<Order> previous() {
        if (!hasPrevious()) {
            return Optional.empty();
        }
        position--;
        return loadCurrent();
    }

    /** True once a result set is loaded (the pager is then shown). */
    public boolean hasResultSet() {
        return position >= 0;
    }

    /** False at the last record — next is disabled there. */
    public boolean hasNext() {
        return position >= 0 && position < resultSet.size() - 1;
    }

    /** False at the first record — prev is disabled there. */
    public boolean hasPrevious() {
        return position > 0;
    }

    /** The current position, 1-based, for the "record N of M" indicator. */
    public int recordNumber() {
        return position + 1;
    }

    /** The size of the result set, for the "record N of M" indicator. */
    public int recordCount() {
        return resultSet.size();
    }

    /** Clears the cursor — the view calls this when the search is emptied. */
    public void clear() {
        this.resultSet = List.of();
        this.position = -1;
    }

    private Optional<Order> loadCurrent() {
        if (position < 0 || position >= resultSet.size()) {
            return Optional.empty();
        }
        return orderService.load(resultSet.get(position).id());
    }

    private int indexOf(long orderId) {
        for (int i = 0; i < resultSet.size(); i++) {
            if (resultSet.get(i).id() == orderId) {
                return i;
            }
        }
        return -1;
    }
}
