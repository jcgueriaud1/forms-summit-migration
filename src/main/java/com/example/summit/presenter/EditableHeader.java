package com.example.summit.presenter;

import com.example.summit.domain.Order;

import java.time.LocalDate;

/**
 * The <strong>mutable</strong> working copy of an order's {@code S_ORD} header, bound to the
 * editable header form (issue #18). The immutable domain {@link Order} is the load/truth
 * representation; a Vaadin {@code Binder} writes the clerk's edits back into a bean, so the
 * editable header needs this mutable mirror — exactly as {@link EditableLine} mirrors a loaded
 * {@code S_ITEM} line for the detail grid.
 *
 * <p>It holds no rule of its own: the ship-date guard and the credit guard stay authoritative in
 * the domain ({@link Order#shipDateValid}, {@link com.example.summit.domain.PaymentEligibility},
 * ADR-0001). {@code customerName}, {@code salesRepId} and {@code salesRepName} are
 * lookup-defaulted display values — re-defaulted from the customer master whenever the clerk
 * changes {@code customerId} (the {@code WHEN-VALIDATE-ITEM} defaulter). Persisting these edits is
 * the commit slice's job; this slice keeps the editing in memory.
 */
public class EditableHeader {

    private final long id;
    private long customerId;
    private String customerName;
    private LocalDate dateOrdered;
    private LocalDate dateShipped;
    private Long salesRepId;
    private String salesRepName;
    private String paymentType;
    private boolean orderFilled;
    // The optimistic-locking token captured at load (S_ORD.ORA_ROWSCN) — carried untouched through
    // the editing session so the commit can re-check it (concurrency-pessimistic-to-scn, issue #19).
    private final Long rowScn;

    /** Wraps a loaded order's header for editing. */
    public EditableHeader(Order order) {
        this.id = order.id();
        this.customerId = order.customerId();
        this.customerName = order.customerName();
        this.dateOrdered = order.dateOrdered();
        this.dateShipped = order.dateShipped();
        this.salesRepId = order.salesRepId();
        this.salesRepName = order.salesRepName();
        this.paymentType = order.paymentType();
        this.orderFilled = order.orderFilled();
        this.rowScn = order.rowScn();
    }

    public long getId() {
        return id;
    }

    public long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(long customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public LocalDate getDateOrdered() {
        return dateOrdered;
    }

    public void setDateOrdered(LocalDate dateOrdered) {
        this.dateOrdered = dateOrdered;
    }

    public LocalDate getDateShipped() {
        return dateShipped;
    }

    public void setDateShipped(LocalDate dateShipped) {
        this.dateShipped = dateShipped;
    }

    public Long getSalesRepId() {
        return salesRepId;
    }

    public void setSalesRepId(Long salesRepId) {
        this.salesRepId = salesRepId;
    }

    public String getSalesRepName() {
        return salesRepName;
    }

    public void setSalesRepName(String salesRepName) {
        this.salesRepName = salesRepName;
    }

    public String getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(String paymentType) {
        this.paymentType = paymentType;
    }

    public boolean isOrderFilled() {
        return orderFilled;
    }

    public void setOrderFilled(boolean orderFilled) {
        this.orderFilled = orderFilled;
    }

    /** The {@code ORA_ROWSCN} version captured at load — the commit re-checks it (issue #19). */
    public Long getRowScn() {
        return rowScn;
    }
}
