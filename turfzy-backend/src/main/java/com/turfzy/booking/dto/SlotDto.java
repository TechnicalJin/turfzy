package com.turfzy.booking.dto;

import com.turfzy.turf.SlotStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Slot DTO returned to clients.
 * Never expose the raw TimeSlot entity — it contains the Turf reference
 * which would trigger lazy loading and potentially leak internal data.
 */
@Data
@Builder
public class SlotDto {
    private Long id;
    private LocalDate slotDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private BigDecimal price;
    private SlotStatus status;
    private boolean available;   // Convenience field: status == AVAILABLE
}