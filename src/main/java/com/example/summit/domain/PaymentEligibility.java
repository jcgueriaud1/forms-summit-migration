package com.example.summit.domain;

import java.util.Set;

/**
 * The credit-payment rule as a <strong>pure domain predicate</strong> (ADR-0001, issue #47) — the
 * Forms {@code S_ORD} credit guard that refuses a {@code CREDIT} payment for a customer whose credit
 * rating is not sound. A {@code CREDIT} payment is allowed only when the customer's
 * {@code CREDIT_RATING} is {@code GOOD} or {@code EXCELLENT}; a {@code CASH} payment is always
 * allowed. Anything else — a {@code POOR} rating, an unknown/null rating under {@code CREDIT} — is
 * refused, and the view forces the payment back to {@code CASH} with a notice.
 *
 * <p>The <em>rule</em> lives here, in pure Domain, with no Spring/Vaadin/DB dependency; the
 * <em>data</em> it judges (the customer's credit rating) is fetched by the Service and handed in.
 * That split is the point of the ticket: the eligibility decision is unit-testable in isolation,
 * exactly as {@link Order#total(java.util.List)} is.
 */
public final class PaymentEligibility {

    /** The credit ratings sound enough for a {@code CREDIT} payment (the {@code S_CUSTOMER} CHECK less {@code POOR}). */
    private static final Set<String> CREDITWORTHY = Set.of("GOOD", "EXCELLENT");

    /** The {@code S_ORD.PAYMENT_TYPE} code the credit guard gates. A {@code CASH} order is never gated. */
    private static final String CREDIT = "CREDIT";

    private PaymentEligibility() {
    }

    /**
     * Whether a payment of {@code paymentType} is allowed for a customer with the given
     * {@code creditRating}. {@code CASH} (indeed any non-{@code CREDIT} type) is always allowed;
     * {@code CREDIT} is allowed only when {@code creditRating} is {@code GOOD} or {@code EXCELLENT}.
     * A {@code null} credit rating under {@code CREDIT} is refused — an unknown rating is not a
     * licence to extend credit.
     */
    public static boolean allows(String creditRating, String paymentType) {
        if (!CREDIT.equals(paymentType)) {
            return true;
        }
        return creditRating != null && CREDITWORTHY.contains(creditRating);
    }
}
