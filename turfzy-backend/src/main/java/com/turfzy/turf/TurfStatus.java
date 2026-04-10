package com.turfzy.turf;

public enum TurfStatus {
    PENDING_APPROVAL,   // Owner submitted — admin hasn't approved yet
    ACTIVE,             // Live and bookable
    INACTIVE,           // Owner disabled temporarily
    REJECTED            // Admin rejected
}