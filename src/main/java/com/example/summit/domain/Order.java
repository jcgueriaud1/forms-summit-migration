package com.example.summit.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.LongStream;

/**
 * The {@code S_ORD} order as a domain type — the <strong>aggregate root</strong> of the
 * {@code S_ORD}/{@code S_ITEM} master-detail spine. Its customer and sales-rep
 * <em>display names</em> are already resolved on the record: the two Forms POST-QUERY
 * lookups ({@code customer_name}, {@code sales_rep_name}) are dissolved into the join in
 * {@link com.example.summit.data.OrderRepository#loadGraph}, so nothing here triggers a
 * per-field round-trip.
 *
 * <p>The order carries its {@link OrderLine} list as the <strong>unit of load</strong>:
 * {@code loadGraph} brings back the header and its {@code S_ITEM} lines together, so the
 * header and the line-items grid are always coordinated on the same order
 * ({@code S_ORD_S_ITEM}) — selecting a different order reloads the whole graph.
 *
 * <p>{@code salesRepId} / {@code salesRepName} are nullable: {@code S_ORD.sales_rep_id}
 * is a nullable column, so an order with no assigned rep is a valid load. {@code lines}
 * is never null — an order with no line items carries an empty list.
 *
 * <p>{@code scn} is the order's <strong>optimistic-lock token</strong> — the {@code S_ORD}
 * row's {@code ORA_ROWSCN}, read on load and re-checked (verified-unchanged) by the
 * whole-graph commit ({@code concurrency-pessimistic-to-scn}, issue #48), the web
 * replacement for Forms' pessimistic across-think-time row lock. It is opaque to the
 * Domain: the commit passes it back to the guarded {@code UPDATE … WHERE ORA_ROWSCN = ?}.
 * A {@link #isNew() new} order (not yet persisted) carries {@code 0}.
 *
 * <p>{@link #total()} is the {@code TOTAL} summary as a <strong>pure domain method</strong>
 * (ADR-0001): the sum of the lines' {@link OrderLine#itemTotal() ITEM_TOTAL}s — so it
 * inherits the same shipped-not-ordered truth, with no Spring, Vaadin, or DB dependency.
 * The read-only line-items grid binds its {@code Total} footer to this method.
 */
public record Order(
        long id,
        long customerId,
        String customerName,
        LocalDate dateOrdered,
        LocalDate dateShipped,
        Long salesRepId,
        String salesRepName,
        String paymentType,
        boolean orderFilled,
        long scn,
        List<OrderLine> lines) {

    /** Whether this order has never been persisted — its id is unassigned ({@code 0}), so the
     * commit draws one from {@code S_ORD_ID.nextval} and inserts rather than updates (issue #48). */
    public boolean isNew() {
        return id == 0L;
    }

    /**
     * A blank, never-persisted order — the <strong>new-item factory</strong> behind the master's
     * <em>New order</em> record action (issue #49, {@code blockActionClassifiers} {@code block-action-create}).
     * {@code S_ORD} has no {@code WHEN-CREATE-RECORD} / {@code WHEN-NEW-RECORD-INSTANCE} trigger, so there
     * are no defaults to apply: every field starts empty. The id is unassigned ({@link #isNew()} → the
     * commit draws it from {@code S_ORD_ID.nextval} and inserts), the payment type defaults to {@code CASH}
     * — the neutral option of the {@code S_ORD.PAYMENT_TYPE} CHECK domain that the credit guard also falls
     * back to, so a new order carries a value the DB accepts — and the line list is empty for the clerk to
     * fill.
     */
    public static Order blank() {
        return new Order(0L, 0L, null, null, null, null, null, "CASH", false, 0L, List.of());
    }

    /**
     * A copy of this order prepared to insert as a <em>new</em> {@code S_ORD} — the pure encoding of the
     * Forms {@code KEY-DUPREC} clear-list for the master block (issue #49, {@code blockActionClassifiers}
     * {@code block-action-duplicate}), the header counterpart of {@link OrderLine#duplicateForInsert()}.
     * The <em>Duplicate order</em> record action clones the loaded order, then clears exactly the identity
     * the copy must not carry so it commits as an insert rather than colliding with the original: the
     * {@code id} (reassigned from {@code S_ORD_ID.nextval} on commit, {@link #isNew()} → true) and its
     * {@code ORA_ROWSCN} lock token (reset to {@code 0} — a new row has no version yet). Every other header
     * field <em>and the line items</em> are carried over — they are what the clerk is duplicating. {@code S_ORD}
     * has no {@code KEY-DUPREC} trigger, so the clear-list is the identity only ({@code migration-rules.yaml}
     * {@code block-action-duplicate}: "absent it, clear the PK / identity only").
     */
    public Order duplicateForInsert() {
        return new Order(0L, customerId, customerName, dateOrdered, dateShipped,
                salesRepId, salesRepName, paymentType, orderFilled, 0L, lines);
    }

    /**
     * A copy of this order carrying a freshly assigned id — the {@code S_ORD} {@code PRE-INSERT}
     * ({@code S_ORD_ID.nextval}, issue #48): a new order builds its header with the sequence value
     * before the insert, leaving every other field (and the lines) untouched.
     */
    public Order withId(long newId) {
        return new Order(newId, customerId, customerName, dateOrdered, dateShipped,
                salesRepId, salesRepName, paymentType, orderFilled, scn, lines);
    }

    /**
     * The order total — the sum of every line's {@link OrderLine#itemTotal()}, the Forms
     * {@code TOTAL} summary (Summary = Sum). An order with no lines totals to zero, and
     * because {@code itemTotal()} already treats a null price as zero, a half-entered line
     * contributes nothing rather than throwing.
     */
    public BigDecimal total() {
        return total(lines);
    }

    /**
     * The {@code TOTAL} summary over an <em>arbitrary</em> line list — the same {@code Sum} formula
     * as {@link #total()}, exposed so an in-memory editing session can recompute the order total live
     * over its working lines (issue #45) without first rebuilding a whole {@link Order}. Keeping it
     * here keeps the formula authoritative in the Domain (ADR-0001): the footer on load and the live
     * footer recompute share one code path.
     */
    public static BigDecimal total(List<OrderLine> lines) {
        return lines.stream()
                .map(OrderLine::itemTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * The ship-date guard as a <strong>pure domain invariant</strong> (ADR-0001, issue #47) — the
     * Forms {@code S_ORD} {@code WHEN-VALIDATE-RECORD} check {@code date_shipped >= date_ordered}. An
     * order cannot record a shipment before it was ordered. A {@code null} ship date is valid (the
     * order simply has not shipped yet); equal dates are valid (shipped the day it was ordered); only
     * a ship date strictly before the order date is rejected. The view mirrors this to a Binder
     * validator (ADR-0002) for live feedback on the ship-date field.
     */
    public boolean shipDateValid() {
        return shipDateValid(dateOrdered, dateShipped);
    }

    /**
     * The {@code date_shipped >= date_ordered} guard over <em>arbitrary</em> dates — the same rule as
     * {@link #shipDateValid()}, exposed so the header editing session can validate the dates the clerk
     * is typing before rebuilding an {@link Order} (issue #47), keeping the check authoritative in the
     * Domain. A {@code null} ship date is allowed; a {@code null} order date makes the comparison
     * vacuous (nothing to be before), so it too is allowed.
     */
    public static boolean shipDateValid(LocalDate dateOrdered, LocalDate dateShipped) {
        return dateShipped == null || dateOrdered == null || !dateShipped.isBefore(dateOrdered);
    }

    /**
     * The next {@code item_id} for a new line — the Forms {@code S_ITEM} {@code PRE-INSERT} rule
     * {@code NVL(MAX(item_id),0)+1}, scoped <em>per order</em> (issue #45). An order with no lines
     * yields {@code 1}, so two orders both start their lines at {@code 1}, exactly as the per-order
     * Forms assignment did.
     */
    public long nextItemId() {
        return nextItemId(lines.stream().mapToLong(OrderLine::itemId));
    }

    /**
     * {@code NVL(MAX(item_id),0)+1} over an arbitrary stream of existing item ids — exposed so the
     * editing session can assign the next id over its <em>working</em> lines (which may include lines
     * the clerk has just added) through the one authoritative rule, never a re-query.
     */
    public static long nextItemId(LongStream existingItemIds) {
        return existingItemIds.max().orElse(0L) + 1L;
    }

    /**
     * Whether this order still has line items — the predicate behind the {@code S_ORD_S_ITEM}
     * <strong>Non-Isolated</strong> cascade-delete guard ({@code delete-cascade-guard}, issue #45).
     * The Service vetoes a master delete while this is {@code true}, so {@code S_ITEM} detail rows
     * can never be orphaned (the web counterpart of Forms' {@code ON-CHECK-DELETE-MASTER}).
     */
    public boolean hasLineItems() {
        return !lines.isEmpty();
    }
}
