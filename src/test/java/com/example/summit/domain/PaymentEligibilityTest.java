package com.example.summit.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure domain test for the {@code S_ORD} payment-eligibility rule (issue #18) — no Spring, no
 * Vaadin, no DB. Guards the Forms {@code WHEN-RADIO-CHANGED} credit invariant: {@code CREDIT} is
 * allowed only for a {@code GOOD} or {@code EXCELLENT} credit rating, while {@code CASH} is always
 * allowed regardless of rating.
 */
class PaymentEligibilityTest {

    @Test
    void creditIsAllowedForAnExcellentRating() {
        assertTrue(PaymentEligibility.allows("EXCELLENT", "CREDIT"));
    }

    @Test
    void creditIsAllowedForAGoodRating() {
        assertTrue(PaymentEligibility.allows("GOOD", "CREDIT"));
    }

    @Test
    void creditIsRefusedForAPoorRating() {
        assertFalse(PaymentEligibility.allows("POOR", "CREDIT"));
    }

    @Test
    void creditIsRefusedForAnUnknownRating() {
        // A null rating is the NO_DATA_FOUND edge (no such customer) — CREDIT is refused.
        assertFalse(PaymentEligibility.allows(null, "CREDIT"));
    }

    @Test
    void cashIsAlwaysAllowedRegardlessOfRating() {
        assertTrue(PaymentEligibility.allows("POOR", "CASH"));
        assertTrue(PaymentEligibility.allows(null, "CASH"));
        assertTrue(PaymentEligibility.allows("EXCELLENT", "CASH"));
    }
}
