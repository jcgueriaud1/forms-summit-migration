package com.example.summit.data;

import com.example.summit.domain.Customer;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves {@code CustomerRepository} against a real Summit Oracle (Testcontainers) — the read behind
 * the {@code S_ORD} {@code WHEN-VALIDATE-ITEM} customer-id lookup and its credit guard (issue #18).
 * The headline: {@code findById} resolves a customer's name, credit rating and sales rep (with the
 * rep last name joined from {@code S_EMP}) in one shot, and returns {@link Optional#empty()} for an
 * unknown id — the {@code NO_DATA_FOUND} "Invalid Customer Id!" reject.
 */
class CustomerRepositoryIT extends SummitDbIT {

    private final CustomerRepository repository = new CustomerRepository(jdbcClient());

    @Test
    void findByIdResolvesNameCreditRatingAndSalesRep() {
        Customer customer = repository.findById(201).orElseThrow();

        assertEquals(201L, customer.id());
        assertEquals("Unisports", customer.name());
        assertEquals("EXCELLENT", customer.creditRating());
        // The sales-rep last name arrives joined from S_EMP, beside the id.
        assertEquals(1L, customer.salesRepId());
        assertEquals("Magee", customer.salesRepName());
    }

    @Test
    void findByIdReturnsThePoorRatedCustomerForTheCreditGuard() {
        // 290 is the POOR-rated customer — the foil for the credit guard (CREDIT refused).
        Customer customer = repository.findById(290).orElseThrow();

        assertEquals("POOR", customer.creditRating());
    }

    @Test
    void findByIdReturnsEmptyForAnUnknownCustomer() {
        // NO_DATA_FOUND → "Invalid Customer Id!" lives one layer up; the repository reports absence.
        Optional<Customer> missing = repository.findById(999999);
        assertTrue(missing.isEmpty());
    }
}
