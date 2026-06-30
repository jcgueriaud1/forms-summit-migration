package com.example.summit.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.LongStream;

/**
 * The {@code S_ORD} order as a domain aggregate — one loaded order with its customer
 * and sales-rep <em>display names</em> already resolved (the dissolved Forms POST-QUERY
 * lookups) <strong>and its {@code S_ITEM} line items</strong>. The whole graph arrives
 * from {@code OrderRepository.loadGraph} on a single query path, so the view never does
 * per-field or per-line round-trips.
 *
 * <p>The {@link #lines()} list is the unit of load: the {@code S_ORD_S_ITEM} master-detail
 * coordination means header and detail are always loaded — and reloaded — together. An
 * order with no line items carries an empty list.
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
        List<OrderLine> lines) {

    /**
     * The {@code TOTAL} summary: {@code Sum} over each line's {@link OrderLine#itemTotal()}
     * (the Forms {@code TOTAL} Summary item, {@code SummaryFunction="Sum"} over {@code ITEM_TOTAL}).
     * An order with no lines sums to {@link BigDecimal#ZERO}; unpriced lines contribute zero,
     * following {@code itemTotal()}'s null-price handling.
     */
    public BigDecimal total() {
        return total(lines);
    }

    /**
     * The {@code TOTAL} summary over an <em>arbitrary</em> line list — the same formula as
     * {@link #total()}, exposed so the editing session can recompute the order total live as the
     * clerk edits the working lines (issue #16) without first rebuilding a whole {@link Order}.
     * Keeping it here keeps the {@code Sum} formula authoritative in the domain (ADR-0001): the
     * footer-on-load and the live footer recompute share one code path.
     */
    public static BigDecimal total(List<OrderLine> lines) {
        return lines.stream()
                .map(OrderLine::itemTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * The next {@code item_id} for a new line — the Forms {@code S_ITEM} {@code PRE-INSERT} rule
     * {@code NVL(MAX(item_id),0)+1}, scoped <em>per order</em> (issue #16). An order with no lines
     * yields {@code 1}. The id is assigned within the order's own line set, so two orders both
     * start their lines at {@code 1}, exactly as the per-order Forms sequence did.
     */
    public long nextItemId() {
        return nextItemId(lines.stream().mapToLong(OrderLine::itemId));
    }

    /**
     * {@code NVL(MAX(item_id),0)+1} over an arbitrary stream of existing item ids — exposed so the
     * editing session can assign the next id over its <em>working</em> lines (which may include
     * lines the clerk has just added) using the one authoritative rule, not a re-query.
     */
    public static long nextItemId(LongStream existingItemIds) {
        return existingItemIds.max().orElse(0L) + 1L;
    }

    /**
     * Whether this order still has line items — the predicate behind the {@code S_ORD_S_ITEM}
     * <strong>Non-Isolated</strong> cascade-delete guard ({@code delete-cascade-guard}, issue #16).
     * The Service vetoes a master delete while this is {@code true}, so {@code S_ITEM} detail rows
     * can never be orphaned (the web counterpart of Forms' {@code ON-CHECK-DELETE-MASTER}).
     */
    public boolean hasLineItems() {
        return !lines.isEmpty();
    }
}
