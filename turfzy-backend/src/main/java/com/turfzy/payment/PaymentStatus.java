package com.turfzy.payment;

public enum PaymentStatus {
    CREATED,        // Razorpay order created
    SUCCESS,        // Payment verified
    FAILED,         // Payment failed at gateway
    REFUND_INITIATED,
    REFUNDED
}