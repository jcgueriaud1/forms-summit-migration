package com.example.summit.presenter;

import com.example.summit.domain.CommitResult;
import com.example.summit.domain.Customer;
import com.example.summit.domain.Order;
import com.example.summit.domain.OrderLine;
import com.example.summit.domain.OrderSearchCriteria;
import com.example.summit.domain.OrderSummary;
import com.example.summit.domain.PaymentEligibility;
import com.example.summit.domain.Product;
import com.example.summit.domain.ProductDefaults;
import com.example.summit.service.CustomerService;
import com.example.summit.service.OrderService;
import com.example.summit.service.ProductService;
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
    private final ProductService productService;
    private final CustomerService customerService;

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

    /**
     * The mutable working copy of the loaded order's header — the editable header form's bean and
     * the target of the {@code WHEN-VALIDATE-ITEM} customer-id defaulter (issue #18). Loading an
     * order seeds it; {@code null} when nothing is loaded.
     */
    private EditableHeader workingHeader;

    public OrderPresenter(OrderService orderService, ProductService productService,
                          CustomerService customerService) {
        this.orderService = orderService;
        this.productService = productService;
        this.customerService = customerService;
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
        this.workingHeader = null;
    }

    /**
     * Starts a brand-new, unsaved order (issue #19): a blank working header ({@link EditableHeader#blank()},
     * so {@link Order#isNew()} holds — the commit will INSERT it with an id from {@code S_ORD_ID.nextval})
     * and an empty working line set for the clerk to fill and add lines to. The search cursor is cleared,
     * since a new order is not part of any result set (the pager hides). The customer-id defaulter and the
     * line editing then work exactly as they do for a loaded order.
     */
    public void newOrder() {
        this.resultSet = List.of();
        this.position = -1;
        this.workingLines = new ArrayList<>();
        this.workingHeader = EditableHeader.blank();
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

    // ---- Product LOV + WHEN-VALIDATE-ITEM defaulter (issue #17) --------------------------------
    // The PRODUCTS_LOV feeds the line's product picker (id + name, writes back only the id); the
    // defaulter is the separate WHEN-VALIDATE-ITEM rule, re-defaulting description + price on any
    // product_id change (LOV pick or direct entry) — orchestration here, the formula-free lookup
    // in ProductService (orders-form-spec.md).

    /** The {@code PRODUCTS_LOV} items (id + name, ordered by name) for the line's lookup ComboBox. */
    public List<Product> products() {
        return productService.lookup();
    }

    /**
     * The {@code WHEN-VALIDATE-ITEM} defaulter: re-defaults {@code line}'s {@code description} and
     * {@code price} from the product, fired on any {@code product_id} change. Returns the applied
     * {@link ProductDefaults} when the product exists, or {@link Optional#empty()} — the Forms
     * {@code NO_DATA_FOUND} reject path — when the id is invalid, in which case the line is left
     * untouched so the caller can reject it. This is deliberately separate from the LOV's own id
     * write-back: it runs the same way for a directly-entered id.
     */
    public Optional<ProductDefaults> applyProductDefaults(EditableLine line, long productId) {
        Optional<ProductDefaults> defaults = productService.findProductDefaults(productId);
        defaults.ifPresent(d -> {
            line.setProductName(d.name());
            line.setPrice(d.suggestedPrice());
        });
        return defaults;
    }

    // ---- Editable master header (issue #18) ----------------------------------------------------
    // The header becomes editable and folds in the S_ORD WHEN-VALIDATE-ITEM customer lookup and the
    // WHEN-RADIO-CHANGED credit guard. The rules are pure Domain (PaymentEligibility, ADR-0001); the
    // data (customer header + credit rating) is the Service's; the orchestration is here.

    /** The working header of the loaded order — the editable header form's bean (issue #18). */
    public EditableHeader editableHeader() {
        return workingHeader;
    }

    /**
     * The {@code S_ORD} {@code WHEN-VALIDATE-ITEM} customer-id defaulter: resolves {@code customerId}
     * against the customer master and re-defaults the working header's {@code customer_name},
     * {@code sales_rep_id} and {@code sales_rep_name} from it (the dissolved Forms {@code SELECT
     * ... INTO}). Returns the resolved {@link Customer} so the view can fill its display fields, or
     * {@link Optional#empty()} — the Forms {@code NO_DATA_FOUND} <em>"Invalid Customer Id!"</em>
     * reject — when the id is unknown, in which case the header is left untouched so the caller can
     * reject it.
     */
    public Optional<Customer> applyCustomerDefaults(long customerId) {
        Optional<Customer> found = customerService.findById(customerId);
        found.ifPresent(customer -> {
            workingHeader.setCustomerId(customer.id());
            workingHeader.setCustomerName(customer.name());
            workingHeader.setSalesRepId(customer.salesRepId());
            workingHeader.setSalesRepName(customer.salesRepName());
        });
        return found;
    }

    /**
     * Whether {@code paymentType} is allowed for the working header's current customer — the
     * {@code WHEN-RADIO-CHANGED} credit guard. The rule is the pure domain
     * {@link PaymentEligibility#allows(String, String)} (ADR-0001); the credit rating it reasons
     * over is fetched here from the customer master. A {@code CREDIT} selection the customer is not
     * eligible for returns {@code false}, on which the view resets the field to {@code CASH} with a
     * notice (the Forms {@code Payment_Type_Alert}). {@code CASH} is always allowed, so no lookup
     * runs for it.
     */
    public boolean paymentAllowed(String paymentType) {
        if (!"CREDIT".equals(paymentType)) {
            return true;
        }
        String creditRating = workingHeader == null
                ? null
                : customerService.findById(workingHeader.getCustomerId())
                        .map(Customer::creditRating)
                        .orElse(null);
        return PaymentEligibility.allows(creditRating, paymentType);
    }

    // ---- Whole-graph commit (COMMIT_FORM, issue #19) ------------------------------------------
    // The one Commit action: assemble the working header + working lines into a domain Order and
    // hand it to the Service's single @Transactional whole-graph save. The Service is the
    // authoritative gate (ADR-0002); the presenter only orchestrates and, on success, reloads so
    // further edits build on the persisted state (fresh id + ORA_ROWSCN).

    /**
     * Commits the loaded, edited order — header + all line items — through
     * {@link OrderService#save(Order)} as one transaction, returning its typed {@link CommitResult}.
     * On {@link CommitResult.Committed} the working state is reloaded from the persisted order (in a
     * fresh read, so it carries the moved {@code ORA_ROWSCN}) and the cursor repositioned on it, so a
     * follow-up edit-and-commit does not falsely conflict. A {@link CommitResult.Conflict} or
     * {@link CommitResult.Rejected} leaves the working state untouched for the clerk to reconcile.
     */
    public CommitResult commit() {
        if (workingHeader == null) {
            return new CommitResult.Rejected("There is no order to commit.");
        }
        CommitResult result = orderService.save(assembleWorkingOrder());
        if (result instanceof CommitResult.Committed committed) {
            reseedWorkingState(orderService.load(committed.orderId()));
        }
        return result;
    }

    /** The working header + working lines as a domain {@link Order} to save — carries the load scn. */
    private Order assembleWorkingOrder() {
        List<OrderLine> lines = workingLines.stream().map(EditableLine::toOrderLine).toList();
        return new Order(
                workingHeader.getId(), workingHeader.getCustomerId(), workingHeader.getCustomerName(),
                workingHeader.getDateOrdered(), workingHeader.getDateShipped(),
                workingHeader.getSalesRepId(), workingHeader.getSalesRepName(),
                workingHeader.getPaymentType(), workingHeader.isOrderFilled(),
                lines, workingHeader.getRowScn());
    }

    private Optional<Order> loadCurrent() {
        if (position < 0 || position >= resultSet.size()) {
            return Optional.empty();
        }
        Optional<Order> loaded = orderService.load(resultSet.get(position).id());
        reseedWorkingState(loaded);
        return loaded;
    }

    /**
     * Seeds fresh working copies of the header and lines from a (re)loaded order — the single place
     * the editable session is reset from truth, shared by load, pager steps, and post-commit reload.
     * A freshly loaded order seeds a fresh working set; an empty load leaves none.
     */
    private void reseedWorkingState(Optional<Order> loaded) {
        this.workingLines = loaded
                .map(order -> new ArrayList<>(order.lines().stream().map(EditableLine::new).toList()))
                .orElseGet(ArrayList::new);
        this.workingHeader = loaded.map(EditableHeader::new).orElse(null);
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
