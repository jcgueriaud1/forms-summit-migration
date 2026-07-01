package com.example.summit.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for {@link PaymentEligibility} — the credit-payment rule as a pure domain predicate
 * (ADR-0001, issue #47), no Spring/Vaadin/DB. A CREDIT payment is allowed only for a GOOD or
 * EXCELLENT credit rating; CASH is always allowed; a POOR or unknown rating refuses CREDIT. The
 * rating is fetched by the Service; only the decision is proven here.
 */
class PaymentEligibilityTest {

    @Test
    void creditIsAllowedForAGoodRating() {
        assertTrue(PaymentEligibility.allows("GOOD", "CREDIT"));
    }

    @Test
    void creditIsAllowedForAnExcellentRating() {
        assertTrue(PaymentEligibility.allows("EXCELLENT", "CREDIT"));
    }

    @Test
    void creditIsRefusedForAPoorRating() {
        assertFalse(PaymentEligibility.allows("POOR", "CREDIT"));
    }

    @Test
    void creditIsRefusedForAnUnknownRating() {
        // A null rating (unknown customer, or a customer whose CREDIT_RATING is null) is not a licence
        // to extend credit.
        assertFalse(PaymentEligibility.allows(null, "CREDIT"));
    }

    @Test
    void cashIsAlwaysAllowedRegardlessOfRating() {
        assertTrue(PaymentEligibility.allows("POOR", "CASH"));
        assertTrue(PaymentEligibility.allows(null, "CASH"));
        assertTrue(PaymentEligibility.allows("EXCELLENT", "CASH"));
    }
}
