package com.example.summit.presenter;

import com.example.summit.domain.CustomerHeader;
import com.example.summit.domain.Order;
import com.example.summit.domain.OrderSearchCriteria;
import com.example.summit.domain.OrderSummary;
import com.example.summit.domain.PaymentEligibility;
import com.example.summit.domain.Product;
import com.example.summit.domain.ProductDefaults;
import com.example.summit.domain.OrderLine;
import com.example.summit.service.CustomerService;
import com.example.summit.service.OrderService;
import com.example.summit.service.ProductService;
import com.example.summit.service.SaveResult;
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
 *
 * <p><strong>Master-header editing (issue #47).</strong> The presenter orchestrates the two
 * {@code S_ORD} header behaviours that need the database: the {@code WHEN-VALIDATE-ITEM} customer
 * lookup ({@link #lookupCustomer}), which defaults the customer name and sales rep onto the header,
 * and the credit guard ({@link #paymentAllowed}), which fetches the customer's credit rating through
 * the {@link CustomerService} and hands it to the pure Domain {@link PaymentEligibility} rule. The
 * ship-date guard needs no service — it is the pure {@link Order#shipDateValid} check the view mirrors
 * to a Binder validator (ADR-0002).
 *
 * <p><strong>Master record actions (issue #49).</strong> The presenter orchestrates the {@code S_ORD}
 * record-action family derived from the block's CRUD capability: {@link #newOrder()} opens a blank order
 * from the {@link Order#blank() new-item factory}, {@link #duplicateOrder} clones the order in hand via
 * the pure Domain {@link Order#duplicateForInsert()} ({@code KEY-DUPREC} clear-list), and {@link #delete}
 * routes to the Service delete path that enforces the Non-Isolated {@code delete-cascade-guard}. New and
 * duplicated orders are committed as inserts through the same whole-graph {@link #commit} path (#48).
 */
@Component
public class OrderPresenter {

    private final OrderService orderService;
    private final ProductService productService;
    private final CustomerService customerService;

    /** The result set the pager walks — the orders the committed search returned. */
    private List<OrderSummary> resultSet = List.of();
    /** Zero-based index into {@link #resultSet}; {@code -1} when no order is loaded. */
    private int position = -1;
    /** The mutable working copies of the loaded order's lines — the editable grid's items. */
    private List<EditableLine> workingLines = new ArrayList<>();

    public OrderPresenter(OrderService orderService, ProductService productService,
                          CustomerService customerService) {
        this.orderService = orderService;
        this.productService = productService;
        this.customerService = customerService;
    }

    /**
     * Autocomplete suggestions for the criteria the clerk has entered so far — the quick term
     * narrowed by whatever advanced filters are set (issue #43). Throwaway per keystroke; it does not
     * touch the committed pager position.
     */
    public List<OrderSummary> suggest(OrderSearchCriteria criteria) {
        return orderService.search(criteria);
    }

    /**
     * Commits the search from a picked suggestion: runs {@code findByCriteria} for the criteria to fix
     * the result set the pager will walk, points the position at the picked order, and loads it. The
     * dropdown suggestions are throwaway per keystroke; this is the definitive search whose result set
     * the pager steps through. Returns the loaded order, or empty if it vanished between suggestion and
     * commit.
     */
    public Optional<Order> select(OrderSearchCriteria criteria, long orderId) {
        resultSet = orderService.search(criteria);
        position = indexOf(orderId);
        return load(orderId);
    }

    /**
     * Commits an advanced search that was not driven by a picked suggestion (the Search button, issue
     * #43): fixes the result set the pager walks and lands on its first match, so the clerk can step
     * through the rest. Returns the first matching order, or empty when nothing matches (the position
     * is cleared so the view returns to the empty state).
     */
    public Optional<Order> runSearch(OrderSearchCriteria criteria) {
        resultSet = orderService.search(criteria);
        position = resultSet.isEmpty() ? -1 : 0;
        return position < 0 ? Optional.empty() : load(currentOrderId());
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

    // ---- Master record actions (issue #49) ----------------------------------------------------

    /**
     * The master's <em>New order</em> record action ({@code block-action-create}): opens a blank editable
     * order from the {@link Order#blank() new-item factory} and seeds an empty working-line set for it.
     * The unsaved order is not part of any search result, so the pager position is dropped — there is
     * nothing to walk. Committing it inserts via {@code S_ORD_ID.nextval} (issue #48). Returns the blank
     * order so the view can render it editable.
     */
    public Order newOrder() {
        Order blank = Order.blank();
        startEditing(blank);
        return blank;
    }

    /**
     * The master's <em>Duplicate order</em> record action ({@code block-action-duplicate}): clones the
     * order currently being edited — the header as the clerk has it plus the working lines — into a new,
     * unsaved editable order via the pure Domain {@link Order#duplicateForInsert()} ({@code KEY-DUPREC}
     * clear-list: nulls the id so it commits as an insert), and re-seeds the working lines from the copy.
     * Like {@link #newOrder()} the copy is outside any search result, so the pager position is dropped.
     * Returns the copy for the view to render editable.
     */
    public Order duplicateOrder(EditableHeader header) {
        Order copy = orderFrom(header, workingLines).duplicateForInsert();
        startEditing(copy);
        return copy;
    }

    /**
     * The master's <em>Delete order</em> record action ({@code block-action-delete}) — deletes through the
     * {@link OrderService#delete Service delete path}, which enforces the {@code S_ORD_S_ITEM}
     * <strong>Non-Isolated</strong> {@code delete-cascade-guard}: an order that still has line items is
     * vetoed with a {@link com.example.summit.domain.CascadeDeleteBlockedException} so detail rows are
     * never orphaned (issue #45). The presenter only invokes the path; the guard decision lives in the
     * Domain/Service (ADR-0001/0002).
     */
    public void delete(long orderId) {
        orderService.delete(orderId);
    }

    /**
     * Begins an in-memory editing session over an order that is <em>not</em> a search result — a blank New
     * order or a Duplicate copy (issue #49). Seeds a fresh set of {@link EditableLine} working copies from
     * the order's lines (empty for a blank order) and drops the pager position, so the view hides the pager
     * until the next search. The pieced-together order is committed through the same whole-graph
     * {@link #commit} path a loaded order is.
     */
    private void startEditing(Order order) {
        resultSet = List.of();
        position = -1;
        workingLines = new ArrayList<>(order.lines().stream().map(EditableLine::new).toList());
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

    // ---- Master-header editing behaviours (issue #47) -----------------------------------------

    /**
     * The {@code S_ORD} {@code WHEN-VALIDATE-ITEM} customer lookup — the {@code name} and default sales
     * rep a chosen customer defaults onto the order header, fetched through the {@link CustomerService}.
     * An id that is not a real customer returns {@link Optional#empty()} (the Forms {@code NO_DATA_FOUND}),
     * which the view surfaces as a rejection on the customer-id field.
     */
    public Optional<CustomerHeader> lookupCustomer(long customerId) {
        return customerService.findCustomerHeader(customerId);
    }

    /**
     * The credit guard: whether {@code paymentType} is allowed for the given customer. The presenter
     * fetches the customer's credit rating through the {@link CustomerService} (the <em>data</em>) and
     * hands it to the pure Domain {@link PaymentEligibility#allows} rule (the <em>decision</em>, ADR-0001).
     * A {@code CASH} payment is always allowed and needs no fetch decision; a {@code CREDIT} payment is
     * allowed only when the customer's rating is {@code GOOD} or {@code EXCELLENT}. An unknown customer
     * carries no creditworthy rating, so {@code CREDIT} is refused.
     */
    public boolean paymentAllowed(long customerId, String paymentType) {
        String creditRating = customerService.findCreditRating(customerId).orElse(null);
        return PaymentEligibility.allows(creditRating, paymentType);
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

    // ---- Whole-graph commit (issue #48) -------------------------------------------------------

    /**
     * Commits the loaded order as one whole-graph {@code COMMIT_FORM} save: assembles the edited
     * {@link EditableHeader} and the working {@link EditableLine} set back into an immutable
     * {@link Order} and hands it to the {@link OrderService#save Service commit} — the authoritative
     * validation + {@code ORA_ROWSCN} concurrency gate. On success the working lines are re-seeded
     * from the freshly saved graph (new item ids, advanced scn) so the clerk can keep editing and
     * re-commit; a conflict or invalid result leaves the working set untouched for the clerk to
     * reconcile. The typed {@link SaveResult} is returned for the view to react to.
     */
    public SaveResult commit(EditableHeader header) {
        SaveResult result = orderService.save(orderFrom(header, workingLines));
        if (result instanceof SaveResult.Committed committed) {
            workingLines = new ArrayList<>(
                    committed.order().lines().stream().map(EditableLine::new).toList());
        }
        return result;
    }

    /** Reassembles the mutable header + working lines into the immutable {@link Order} the commit saves,
     * carrying the {@code ORA_ROWSCN} token so the Service can re-check it. */
    private static Order orderFrom(EditableHeader header, List<EditableLine> lines) {
        return new Order(
                header.getId(), header.getCustomerId(), header.getCustomerName(),
                header.getDateOrdered(), header.getDateShipped(),
                header.getSalesRepId(), header.getSalesRepName(),
                header.getPaymentType(), header.isOrderFilled(), header.getScn(),
                lines.stream().map(EditableLine::toOrderLine).toList());
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
