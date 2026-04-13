// src/main/java/com/turfzy/booking/dto/BookingRequest.java
package com.turfzy.booking.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BookingRequest {

    @NotNull(message = "Slot ID is required")
    private Long slotId;
}