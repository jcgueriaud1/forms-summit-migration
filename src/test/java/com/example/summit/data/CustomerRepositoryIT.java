package com.example.summit.data;

import com.example.summit.domain.CustomerHeader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves {@code CustomerRepository} against a real Summit Oracle (Testcontainers, via the shared
 * {@code SummitDbIT} harness) — the two reads behind the ORDERS master-header editing behaviours
 * (issue #47).
 *
 * <p>The {@code WHEN-VALIDATE-ITEM} customer lookup ({@link CustomerRepository#findCustomerHeader})
 * returns the {@code name} and default sales rep a chosen customer defaults onto the order header, and
 * an empty {@link java.util.Optional} for an id that is not a real customer (the {@code NO_DATA_FOUND}
 * case). The credit read ({@link CustomerRepository#findCreditRating}) returns the {@code CREDIT_RATING}
 * the Domain rule judges.
 */
class CustomerRepositoryIT extends SummitDbIT {

    private final CustomerRepository repository = new CustomerRepository(jdbcClient());

    @Test
    void findCustomerHeaderReturnsNameAndDefaultSalesRep() {
        // 201 is "Unisports", sales rep 1 (Magee) — the columns the lookup defaults onto the header.
        CustomerHeader header = repository.findCustomerHeader(201).orElseThrow();

        assertEquals("Unisports", header.name());
        assertEquals(1L, header.salesRepId());
        assertEquals("Magee", header.salesRepName());
    }

    @Test
    void findCustomerHeaderIsEmptyForAnUnknownCustomer() {
        // No such customer -> the NO_DATA_FOUND branch the view rejects.
        assertTrue(repository.findCustomerHeader(999999).isEmpty());
    }

    @Test
    void findCreditRatingReturnsTheRating() {
        // 201 Unisports is EXCELLENT; 290 Kam's Sporting Goods is POOR — the foil for the credit guard.
        assertEquals("EXCELLENT", repository.findCreditRating(201).orElseThrow());
        assertEquals("POOR", repository.findCreditRating(290).orElseThrow());
    }

    @Test
    void findCreditRatingIsEmptyForAnUnknownCustomer() {
        assertTrue(repository.findCreditRating(999999).isEmpty());
    }
}
