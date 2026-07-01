package com.example.summit.data;

import com.example.summit.domain.CustomerHeader;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Data layer for {@code S_CUSTOMER} — the two reads behind the ORDERS master-header editing
 * behaviours (issue #47). All SQL lives here; the view, presenter, and service hold none. Table names
 * are <strong>unqualified</strong> for the same reason {@link OrderRepository} gives — the app and the
 * IT harness both resolve them against their own {@code summit} schema.
 *
 * <p>The two reads are deliberately separate, mirroring the Forms constructs they migrate: the
 * {@code WHEN-VALIDATE-ITEM} customer lookup defaults the {@code name} and default sales rep onto the
 * order header, while the credit guard needs only the {@code credit_rating} the Domain
 * {@link com.example.summit.domain.PaymentEligibility} rule judges. Keeping them apart keeps the
 * defaulter and the credit check independent — the credit rating is fetched only when a
 * {@code CREDIT} payment is attempted.
 */
@Repository
public class CustomerRepository {

    private final JdbcClient jdbcClient;

    public CustomerRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * The {@code WHEN-VALIDATE-ITEM} customer lookup — the {@code name} and default sales rep a chosen
     * customer defaults onto the order header. {@code S_EMP} joins left because
     * {@code S_CUSTOMER.SALES_REP_ID} is nullable — a customer with no rep still resolves, with a
     * {@code null} rep id and name. An id that is not a real customer returns {@link Optional#empty()}:
     * the {@code NO_DATA_FOUND} branch the view rejects (the Forms {@code RAISE FORM_TRIGGER_FAILURE}).
     */
    public Optional<CustomerHeader> findCustomerHeader(long customerId) {
        return jdbcClient
                .sql("""
                        SELECT c.name, c.sales_rep_id, e.last_name AS sales_rep_name
                        FROM s_customer c
                        LEFT JOIN s_emp e ON e.id = c.sales_rep_id
                        WHERE c.id = :id
                        """)
                .param("id", customerId)
                .query((rs, rowNum) -> new CustomerHeader(
                        rs.getString("name"),
                        rs.getObject("sales_rep_id", Long.class),
                        rs.getString("sales_rep_name")))
                .optional();
    }

    /**
     * The credit guard's read — a customer's {@code CREDIT_RATING} ({@code EXCELLENT}/{@code GOOD}/
     * {@code POOR}), or {@link Optional#empty()} for an unknown customer. The rating is only the
     * <em>data</em>; whether it permits a {@code CREDIT} payment is the Domain
     * {@link com.example.summit.domain.PaymentEligibility#allows} rule's call. {@code CREDIT_RATING}
     * is itself nullable, so a present customer may still carry a {@code null} rating — which the rule
     * treats as not creditworthy.
     */
    public Optional<String> findCreditRating(long customerId) {
        return jdbcClient
                .sql("SELECT credit_rating FROM s_customer WHERE id = :id")
                .param("id", customerId)
                .query((rs, rowNum) -> rs.getString("credit_rating"))
                .optional();
    }
}
