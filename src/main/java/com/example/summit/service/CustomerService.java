package com.example.summit.service;

import com.example.summit.data.CustomerRepository;
import com.example.summit.domain.Customer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service layer for {@code S_CUSTOMER} — the read side of the {@code S_ORD} customer-id lookup and
 * its credit guard (issue #18). The lookup is the <em>data</em> half of the Forms
 * {@code WHEN-VALIDATE-ITEM} trigger ({@code orders-form-spec.md}); the rules that act on it live
 * in the domain ({@link com.example.summit.domain.PaymentEligibility}) and the orchestration in the
 * presenter. Read-only; it owns the transactional boundary and delegates the SQL to
 * {@link CustomerRepository}.
 */
@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    /**
     * Resolves a customer by id (name, credit rating, sales rep), or {@link Optional#empty()} when
     * no such customer exists — the {@code NO_DATA_FOUND} "Invalid Customer Id!" reject path.
     */
    @Transactional(readOnly = true)
    public Optional<Customer> findById(long customerId) {
        return customerRepository.findById(customerId);
    }
}
