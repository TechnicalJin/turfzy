package com.turfzy.booking;

public enum BookingStatus {
    PENDING,        // Payment initiated but not confirmed
    CONFIRMED,      // Payment successful
    CANCELLED,      // User cancelled
    REFUNDED,       // Refund processed
    NO_SHOW         // User didn't show up (future feature)
}