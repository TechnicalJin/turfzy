// src/main/java/com/turfzy/booking/dto/BookingDto.java
package com.turfzy.booking.dto;

import com.turfzy.booking.BookingStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Full booking response DTO.
 * Contains everything the customer needs to confirm their booking.
 */
@Data
@Builder
public class BookingDto {
    private Long id;
    private String bookingReference;
    private BookingStatus status;
    private BigDecimal totalAmount;
    private LocalDateTime createdAt;
    private LocalDateTime cancelledAt;
    private String cancellationReason;

    // Slot details
    private Long slotId;
    private LocalDate slotDate;
    private LocalTime startTime;
    private LocalTime endTime;

    // Turf details
    private Long turfId;
    private String turfName;
    private String turfCity;
    private String turfAddress;

    // Customer details
    private Long customerId;
    private String customerName;
    private String customerEmail;
}