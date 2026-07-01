package com.example.summit.presenter;

import com.example.summit.domain.Order;
import com.example.summit.domain.OrderSearchCriteria;
import com.example.summit.domain.OrderSummary;
import com.example.summit.domain.Product;
import com.example.summit.domain.ProductDefaults;
import com.example.summit.service.OrderService;
import com.example.summit.service.ProductService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Presenter layer — owns <em>orchestration</em>. It wraps the clerk's typed term in an
 * {@link OrderSearchCriteria} for the autocomplete and loads whichever order the clerk
 * picks. It calls the Service only; it holds no SQL and no business formula.
 *
 * <p><strong>Result-set position (issue #42).</strong> The presenter — not the view —
 * owns the current position within the result set {@code findByCriteria} returned, the
 * replacement for Forms' record scrollbar. Committing a search ({@link #select}) fixes
 * that set and points at the picked order; {@link #previous()}/{@link #next()} step
 * through it, each reusing the single-order load path ({@link #load}). {@link #hasPrevious()}
 * is false at the first match, {@link #hasNext()} false at the last, and
 * {@link #currentRecord()}/{@link #resultCount()} feed the "record N of M" indicator.
 *
 * <p><strong>In-memory line editing (issue #45).</strong> Loading an order also seeds a fresh set of
 * mutable {@link EditableLine} working copies; the editable grid binds to them and the order
 * {@link #currentTotal() total} is summed over them live as the clerk edits, adds, duplicates, or
 * removes lines. ADR-0002: the presenter orchestrates the recompute (the view's Binder listeners
 * call back in), no Signals. Persisting the working set is the commit slice's job.
 *
 * <p><strong>Product LOV + defaulter (issue #46).</strong> The presenter feeds the line's
 * {@code PRODUCTS_LOV} its reference list ({@link #availableProducts()}) and owns the
 * {@code WHEN-VALIDATE-ITEM} defaulter ({@link #applyProductDefaults}): on any {@code product_id}
 * change — a LOV pick or a directly typed id — it looks the product up through the {@link ProductService}
 * and writes the product's name and suggested price onto the working line's description and price.
 * An id that is not a real product is rejected (the Forms {@code NO_DATA_FOUND → RAISE}); the view
 * turns the rejection into a message on the field.
 */
@Component
public class OrderPresenter {

    private final OrderService orderService;
    private final ProductService productService;

    /** The result set the pager walks — the orders the committed search returned. */
    private List<OrderSummary> resultSet = List.of();
    /** Zero-based index into {@link #resultSet}; {@code -1} when no order is loaded. */
    private int position = -1;
    /** The mutable working copies of the loaded order's lines — the editable grid's items. */
    private List<EditableLine> workingLines = new ArrayList<>();

    public OrderPresenter(OrderService orderService, ProductService productService) {
        this.orderService = orderService;
        this.productService = productService;
    }

    /** Quick-search suggestions for the term the clerk has typed so far. */
    public List<OrderSummary> suggest(String quickTerm) {
        return orderService.search(new OrderSearchCriteria(quickTerm));
    }

    /**
     * Commits the quick search: runs {@code findByCriteria} for the term to fix the result
     * set the pager will walk, points the position at the picked order, and loads it. The
     * dropdown suggestions are throwaway per keystroke; this is the definitive search whose
     * result set the pager steps through. Returns the loaded order, or empty if it vanished
     * between suggestion and commit.
     */
    public Optional<Order> select(String quickTerm, long orderId) {
        resultSet = orderService.search(new OrderSearchCriteria(quickTerm));
        position = indexOf(orderId);
        return load(orderId);
    }

    /** Steps to the previous match and loads it; empty (position unchanged) at the first record. */
    public Optional<Order> previous() {
        if (!hasPrevious()) {
            return Optional.empty();
        }
        position--;
        return load(currentOrderId());
    }

    /** Steps to the next match and loads it; empty (position unchanged) at the last record. */
    public Optional<Order> next() {
        if (!hasNext()) {
            return Optional.empty();
        }
        position++;
        return load(currentOrderId());
    }

    /** Clears the result-set position and working lines — nothing to walk or edit until the next search. */
    public void reset() {
        resultSet = List.of();
        position = -1;
        workingLines = new ArrayList<>();
    }

    // ---- In-memory line editing (issue #45) ---------------------------------------------------

    /** The working lines of the loaded order — the editable grid's items. */
    public List<EditableLine> editableLines() {
        return workingLines;
    }

    /**
     * Appends a blank new line with its assigned {@code item_id} — {@code NVL(MAX(item_id),0)+1} over
     * the <em>working</em> lines, so an id added after other new lines still follows the per-order
     * Forms {@code PRE-INSERT} rule ({@link Order#nextItemId(java.util.stream.LongStream)}). Returns
     * the new line so the view can open the editor on it.
     */
    public EditableLine addLine() {
        EditableLine line = EditableLine.blank(nextWorkingItemId());
        workingLines.add(line);
        return line;
    }

    /**
     * Duplicates a line for insert — the {@code KEY-DUPREC} row action. The clear-list is the
     * Domain's ({@link com.example.summit.domain.OrderLine#duplicateForInsert()}): {@code item_id},
     * {@code product_id}, {@code description} and {@code price} are cleared and the quantities carried
     * over; the copy is then given the next per-order {@code item_id} so it commits as an insert.
     */
    public EditableLine duplicateLine(EditableLine selected) {
        EditableLine copy = new EditableLine(selected.toOrderLine().duplicateForInsert());
        copy.setItemId(nextWorkingItemId());
        workingLines.add(copy);
        return copy;
    }

    /** Removes a line from the working set (the in-memory detail delete). */
    public void removeLine(EditableLine line) {
        workingLines.remove(line);
    }

    // ---- Product LOV + WHEN-VALIDATE-ITEM defaulter (issue #46) --------------------------------

    /**
     * The {@code PRODUCTS_LOV} reference list — {@code (id, name)} ordered by name — that feeds the
     * line's product lookup ({@code orders-form-spec.md} §LOV). Reference data, so the view loads it
     * once at init (the Forms {@code WHEN-NEW-FORM-INSTANCE} record-group populate).
     */
    public List<Product> availableProducts() {
        return productService.lookupProducts();
    }

    /**
     * The {@code S_ITEM} {@code WHEN-VALIDATE-ITEM} defaulter (rule {@code trigger-when-validate-item-default}),
     * fired on <em>any</em> {@code product_id} change — a {@code PRODUCTS_LOV} pick or a directly typed id,
     * kept separate from the pick itself. It looks the product up through the {@link ProductService} and,
     * when it exists, writes the id onto the line and re-defaults the line's {@code description} from the
     * product name and its {@code price} from the suggested wholesale price. An id that is not a real
     * product is the Forms {@code NO_DATA_FOUND}: the line is left untouched and {@code false} is returned,
     * which the view surfaces as a rejection message on the field.
     *
     * @return {@code true} if the product existed and the line was re-defaulted; {@code false} if the id
     *         was not a real product (the line is unchanged)
     */
    public boolean applyProductDefaults(EditableLine line, long productId) {
        Optional<ProductDefaults> defaults = productService.findProductDefaults(productId);
        if (defaults.isEmpty()) {
            return false;
        }
        line.setProductId(productId);
        line.setDescription(defaults.get().name());
        line.setPrice(defaults.get().suggestedPrice());
        return true;
    }

    /**
     * The live order {@code TOTAL} — the {@code Sum} over the working lines' {@code ITEM_TOTAL},
     * recomputed from the current edits via the authoritative Domain formula
     * ({@link Order#total(List)}, ADR-0001). The view calls this to refresh the footer on every edit,
     * add, duplicate, and delete.
     */
    public BigDecimal currentTotal() {
        return Order.total(workingLines.stream().map(EditableLine::toOrderLine).toList());
    }

    private long nextWorkingItemId() {
        return Order.nextItemId(workingLines.stream().mapToLong(EditableLine::getItemId));
    }

    /** True when a previous match exists — false at the first record (Prev disabled). */
    public boolean hasPrevious() {
        return position > 0;
    }

    /** True when a next match exists — false at the last record (Next disabled). */
    public boolean hasNext() {
        return position >= 0 && position < resultSet.size() - 1;
    }

    /** The current match as a 1-based record number for the indicator; {@code 0} when none is loaded. */
    public int currentRecord() {
        return position + 1;
    }

    /** The size of the walked result set — the "M" in "record N of M". */
    public int resultCount() {
        return resultSet.size();
    }

    /** The summary of the current match, so the view can reflect it in the search field while paging. */
    public Optional<OrderSummary> currentSummary() {
        return position < 0 ? Optional.empty() : Optional.of(resultSet.get(position));
    }

    /**
     * Loads the picked order's header (customer and sales-rep names joined) and seeds a fresh set of
     * {@link EditableLine} working copies for the editable grid — a failed load leaves an empty set.
     * All the load paths ({@link #select}, {@link #previous()}, {@link #next()}) funnel through here,
     * so switching orders always re-seeds the working lines.
     */
    public Optional<Order> load(long orderId) {
        Optional<Order> loaded = orderService.load(orderId);
        workingLines = loaded
                .map(order -> new ArrayList<>(order.lines().stream().map(EditableLine::new).toList()))
                .orElseGet(ArrayList::new);
        return loaded;
    }

    private long currentOrderId() {
        return resultSet.get(position).id();
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
