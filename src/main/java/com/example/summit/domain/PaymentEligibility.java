package com.example.summit.domain;

/**
 * The {@code S_ORD} payment-eligibility rule (issue #18) — the pure invariant behind the Forms
 * {@code WHEN-RADIO-CHANGED} credit guard on {@code PAYMENT_TYPE}. The Forms body refuses a
 * {@code CREDIT} payment for a customer whose {@code credit_rating} is not {@code GOOD} or
 * {@code EXCELLENT}, resetting it to {@code CASH}:
 *
 * <pre>{@code
 * IF :S_ORD.payment_type = 'CREDIT' THEN
 *   SELECT credit_rating INTO v_credit FROM S_CUSTOMER WHERE :S_ORD.customer_id = id;
 *   IF v_credit NOT IN ('GOOD', 'EXCELLENT') THEN
 *     :S_ORD.payment_type := 'CASH';
 *     n := SHOW_ALERT('Payment_Type_Alert');
 *   END IF;
 * END IF;
 * }</pre>
 *
 * <p>The rule itself is a pure, unit-tested domain check (ADR-0001); the credit rating it reasons
 * over is the <em>data</em> the Service fetches from the customer master, and the reset-with-notice
 * is the <em>orchestration</em> the Presenter/View perform on a refused {@code CREDIT} selection.
 */
public final class PaymentEligibility {

    private PaymentEligibility() {
    }

    /**
     * Whether {@code paymentType} is allowed for a customer with the given {@code creditRating}.
     * {@code CASH} (and any non-{@code CREDIT} type) is always allowed; {@code CREDIT} is allowed
     * only for a {@code GOOD} or {@code EXCELLENT} rating. A missing rating ({@code null} — an
     * unknown customer, the {@code NO_DATA_FOUND} edge) is not {@code GOOD}/{@code EXCELLENT}, so
     * {@code CREDIT} is refused.
     */
    public static boolean allows(String creditRating, String paymentType) {
        if (!"CREDIT".equals(paymentType)) {
            return true;
        }
        return "GOOD".equals(creditRating) || "EXCELLENT".equals(creditRating);
    }
}
