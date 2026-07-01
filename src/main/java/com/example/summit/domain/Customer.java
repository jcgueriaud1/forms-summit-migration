package com.example.summit.domain;

/**
 * One {@code S_CUSTOMER} row as the {@code S_ORD} customer-id lookup needs it (issue #18) — the
 * web counterpart of the Forms {@code WHEN-VALIDATE-ITEM} trigger on {@code CUSTOMER_ID}:
 *
 * <pre>{@code
 * SELECT C.name, C.sales_rep_id, E.last_name
 * INTO :S_ORD.customer_name, :S_ORD.sales_rep_id, :S_ORD.sales_rep_name
 * FROM S_CUSTOMER C, S_EMP E
 * WHERE (E.id = C.sales_rep_id) AND (:S_ORD.customer_id = C.id);
 * }</pre>
 *
 * <p>Typing a customer id fills the order's {@code customer_name}, {@code sales_rep_id} and
 * {@code sales_rep_name} from this row; the absence of a row is the Forms {@code NO_DATA_FOUND}
 * &rarr; <em>"Invalid Customer Id!"</em> reject path. {@link #creditRating()} additionally feeds
 * the {@link PaymentEligibility} credit guard — the one piece of customer data the {@code S_ORD}
 * aggregate does not already carry.
 *
 * <p>The Forms equijoin to {@code S_EMP} would itself raise {@code NO_DATA_FOUND} for a customer
 * with no sales rep; the migration resolves the rep with a <em>left</em> join instead, so a
 * rep-less customer still loads (its {@link #salesRepId()} / {@link #salesRepName()} are
 * {@code null}) rather than being rejected as invalid.
 */
public record Customer(
        long id,
        String name,
        String creditRating,
        Long salesRepId,
        String salesRepName) {
}
