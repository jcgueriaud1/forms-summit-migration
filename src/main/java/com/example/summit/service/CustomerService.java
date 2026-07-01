package com.example.summit.service;

import com.example.summit.data.CustomerRepository;
import com.example.summit.domain.CustomerHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service layer for the ORDERS master-header editing lookups (issue #47) — the transactional boundary
 * over the two {@code S_CUSTOMER} reads. Both are read-only: the {@code WHEN-VALIDATE-ITEM} customer
 * lookup that defaults name and sales rep onto the header, and the credit-rating fetch behind the
 * credit guard. The Forms {@code SELECT … INTO} lookups surface here as the {@code @Service} calls the
 * rules prescribe; turning an empty customer result into a rejection, and judging the credit rating
 * against {@link com.example.summit.domain.PaymentEligibility}, are the presenter's and Domain's jobs.
 */
@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    /**
     * The {@code WHEN-VALIDATE-ITEM} customer lookup — the {@code name} and default sales rep a chosen
     * customer defaults onto the order header, or {@link Optional#empty()} if {@code customerId} is not
     * a real customer (the {@code NO_DATA_FOUND} case the caller rejects).
     */
    @Transactional(readOnly = true)
    public Optional<CustomerHeader> findCustomerHeader(long customerId) {
        return customerRepository.findCustomerHeader(customerId);
    }

    /**
     * The credit guard's read — the customer's {@code CREDIT_RATING}, or {@link Optional#empty()} for
     * an unknown customer. The rating is the <em>data</em> the Domain
     * {@link com.example.summit.domain.PaymentEligibility#allows} rule judges.
     */
    @Transactional(readOnly = true)
    public Optional<String> findCreditRating(long customerId) {
        return customerRepository.findCreditRating(customerId);
    }
}
