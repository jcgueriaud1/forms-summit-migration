package com.example.summit.presenter;

import com.example.summit.domain.Order;
import com.example.summit.domain.OrderSearchCriteria;
import com.example.summit.domain.OrderSummary;
import com.example.summit.service.OrderService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
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

    /**
     * The mutable working copies of the loaded order's lines — the editable grid's items and the
     * source the live order {@link #currentTotal() total} is summed over (issue #16). Editing is
     * in-memory this slice; persisting the working set is the commit slice's job.
     */
    private List<EditableLine> workingLines = new ArrayList<>();

    public OrderPresenter(OrderService orderService) {
        this.orderService = orderService;
    }

    /** Search suggestions for the criteria the clerk has built so far (quick term + advanced). */
    public List<OrderSummary> suggest(OrderSearchCriteria criteria) {
        return orderService.search(criteria);
    }

    /**
     * Opens the result set for {@code criteria} and positions the cursor at the {@code picked}
     * suggestion, loading that order. This is the autocomplete entry point the pager then steps
     * through: the result set is every order matching the same criteria the clerk searched, so
     * prev/next walk the clerk's query exactly as Forms' record scrollbar did.
     */
    public Optional<Order> open(OrderSearchCriteria criteria, OrderSummary picked) {
        this.resultSet = orderService.search(criteria);
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

    /**
     * Runs {@code criteria} and loads the result set positioned at its <strong>first</strong>
     * record — the advanced-search entry point. The pager then reflects exactly this set: one
     * match is "record 1 of 1", N matches are "record 1 of N" starting at the first. The cursor
     * is reset on every search, so a fresh search always starts the clerk at record 1; an empty
     * result set returns {@link Optional#empty()} with the cursor cleared (no pager).
     */
    public Optional<Order> search(OrderSearchCriteria criteria) {
        this.resultSet = orderService.search(criteria);
        this.position = resultSet.isEmpty() ? -1 : 0;
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
        this.workingLines = new ArrayList<>();
    }

    // ---- In-memory line editing (issue #16) ---------------------------------------------------
    // Loading an order seeds a fresh working set; the editable grid binds to it and the order
    // total is summed over it live as the clerk edits, adds, or removes lines. ADR-0002: the
    // presenter orchestrates the recompute (the view's Binder listeners call back in), no Signals.

    /** The working lines of the loaded order — the editable grid's items. */
    public List<EditableLine> editableLines() {
        return workingLines;
    }

    /**
     * Appends a new line with its assigned {@code item_id} — {@code NVL(MAX(item_id),0)+1} over the
     * <em>working</em> lines, so an id added after other new lines still follows the per-order
     * Forms {@code PRE-INSERT} rule ({@link Order#nextItemId(java.util.stream.LongStream)}). Returns
     * the new line so the view can open the editor on it.
     */
    public EditableLine addLine() {
        long itemId = Order.nextItemId(workingLines.stream().mapToLong(EditableLine::getItemId));
        EditableLine line = EditableLine.blank(itemId);
        workingLines.add(line);
        return line;
    }

    /** Removes a line from the working set (the in-memory detail delete). */
    public void removeLine(EditableLine line) {
        workingLines.remove(line);
    }

    /**
     * The live order {@code TOTAL} — the {@code Sum} over the working lines' {@code ITEM_TOTAL},
     * recomputed from the current edits via the authoritative domain formula
     * ({@link Order#total(List)}, ADR-0001). The view calls this to refresh the footer on every
     * edit, add, and delete.
     */
    public BigDecimal currentTotal() {
        return Order.total(workingLines.stream().map(EditableLine::toOrderLine).toList());
    }

    private Optional<Order> loadCurrent() {
        if (position < 0 || position >= resultSet.size()) {
            return Optional.empty();
        }
        Optional<Order> loaded = orderService.load(resultSet.get(position).id());
        // A freshly loaded order seeds a fresh working set; a failed load leaves none.
        this.workingLines = loaded
                .map(order -> new ArrayList<>(order.lines().stream().map(EditableLine::new).toList()))
                .orElseGet(ArrayList::new);
        return loaded;
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
