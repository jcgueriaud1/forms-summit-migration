package com.example.summit.data;

import com.example.summit.domain.Customer;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Data layer for the {@code S_CUSTOMER} master — the read behind the {@code S_ORD}
 * {@code WHEN-VALIDATE-ITEM} customer-id lookup and its credit guard (issue #18,
 * {@code orders-form-spec.md}). All SQL lives here; the view and presenter hold none. Table names
 * are unqualified for the same reason as {@link OrderRepository}: the app connects as the schema
 * owner.
 */
@Repository
public class CustomerRepository {

    private final JdbcClient jdbcClient;

    public CustomerRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Resolves a customer by id — {@code name}, {@code credit_rating} and the sales rep
     * ({@code sales_rep_id} + {@code S_EMP.last_name}) the Forms trigger reads. An
     * {@link Optional#empty() empty} result is the {@code NO_DATA_FOUND} reject — an invalid
     * customer id. The sales rep is a <strong>left</strong> join (unlike the Forms equijoin) so a
     * customer with no sales rep still resolves rather than reading as invalid.
     */
    public Optional<Customer> findById(long customerId) {
        return jdbcClient
                .sql("""
                        SELECT c.id, c.name, c.credit_rating,
                               c.sales_rep_id, e.last_name AS sales_rep_name
                        FROM s_customer c
                        LEFT JOIN s_emp e ON e.id = c.sales_rep_id
                        WHERE c.id = :id
                        """)
                .param("id", customerId)
                .query((rs, rowNum) -> new Customer(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("credit_rating"),
                        rs.getObject("sales_rep_id", Long.class),
                        rs.getString("sales_rep_name")))
                .optional();
    }
}
