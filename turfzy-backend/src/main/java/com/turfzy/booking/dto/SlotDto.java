package com.turfzy.booking.dto;

import com.turfzy.turf.SlotStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

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