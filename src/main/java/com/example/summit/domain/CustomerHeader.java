package com.example.summit.domain;

/**
 * The columns the Forms {@code S_ORD} {@code WHEN-VALIDATE-ITEM} customer lookup selects when a clerk
 * enters a {@code customer_id} (issue #47): the customer's {@code name} and its default sales rep
 * ({@code sales_rep_id} and the rep's {@code last_name}). Entering a customer id defaults these onto
 * the order header — {@code customer_name}, {@code sales_rep_id}, {@code sales_rep_name} — the same
 * denormalized display fields the load query joins in ({@link Order}).
 *
 * <p>{@code salesRepId} / {@code salesRepName} are nullable: {@code S_CUSTOMER.SALES_REP_ID} is a
 * nullable column, so a customer with no assigned rep defaults a blank rep onto the order. Absence of
 * the whole record is the {@code NO_DATA_FOUND} case — an id that is not a real customer — which the
 * repository returns as an empty {@link java.util.Optional} and the view turns into a rejection on the
 * customer-id field (the Forms {@code RAISE FORM_TRIGGER_FAILURE}).
 */
public record CustomerHeader(String name, Long salesRepId, String salesRepName) {
}
