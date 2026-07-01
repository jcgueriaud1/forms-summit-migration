package com.example.summit.presenter;

import com.example.summit.domain.Order;

import java.time.LocalDate;

/**
 * The <strong>mutable</strong> working copy of the {@code S_ORD} order header, bound to the editable
 * header {@link com.vaadin.flow.component.formlayout.FormLayout} via a Vaadin {@code Binder} (issue
 * #47). The immutable domain {@link Order} is the load/truth representation; a {@code Binder} writes
 * field edits back into a bean, so the in-place header editor needs this mutable mirror — the header
 * counterpart of {@link EditableLine} for the line items.
 *
 * <p>It holds the header's editable fields plus the two denormalized display fields the
 * {@code WHEN-VALIDATE-ITEM} customer lookup defaults ({@code customerName}, {@code salesRepName}) and
 * the {@code salesRepId} the same lookup fills. It carries <em>no</em> rule of its own: the ship-date
 * guard is {@link Order#shipDateValid(LocalDate, LocalDate)} and the credit guard is
 * {@link com.example.summit.domain.PaymentEligibility}, both pure Domain (ADR-0001). Persisting these
 * edits is the commit slice's job (#48); this slice keeps the header editing in memory.
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

    /** Wraps a loaded order's header for editing — the id is fixed (the PK is not editable). */
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
}
