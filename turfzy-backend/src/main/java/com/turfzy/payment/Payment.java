package com.turfzy.payment;

import com.turfzy.booking.Booking;
import com.turfzy.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Payment — stores Razorpay transaction details for a booking.
 *
 * Razorpay flow (implemented Day 8):
 * 1. We create a Razorpay Order → store razorpayOrderId
 * 2. User pays → we receive razorpayPaymentId + razorpaySignature
 * 3. We verify signature → update status to SUCCESS
 *
 * All three IDs are stored for audit trail and dispute resolution.
 */
@Entity
@Table(
    name = "payments",
    indexes = {
        @Index(name = "idx_payment_booking", columnList = "booking_id"),
        @Index(name = "idx_payment_razorpay_order", columnList = "razorpay_order_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.CREATED;

    // Razorpay IDs — stored for webhook verification + refunds
    @Column(name = "razorpay_order_id", unique = true, length = 100)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", unique = true, length = 100)
    private String razorpayPaymentId;

    @Column(name = "razorpay_signature", length = 255)
    private String razorpaySignature;

    @Column(name = "razorpay_refund_id", length = 100)
    private String razorpayRefundId;

    @Column(name = "failure_reason", length = 300)
    private String failureReason;
}