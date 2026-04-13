package com.turfzy.booking;

import com.turfzy.common.BaseEntity;
import com.turfzy.turf.SlotStatus;
import com.turfzy.turf.Turf;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(
    name = "time_slots",
    uniqueConstraints = {
        // THE MOST IMPORTANT CONSTRAINT IN THE ENTIRE APPLICATION
        @UniqueConstraint(
            name = "uk_turf_date_slot",
            columnNames = {"turf_id", "slot_date", "start_time"}
        )
    },
    indexes = {
        @Index(name = "idx_slot_turf_date", columnList = "turf_id, slot_date"),
        @Index(name = "idx_slot_status", columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeSlot extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turf_id", nullable = false)
    private Turf turf;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;     // Always startTime + 1 hour for MVP

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;      // Snapshot of price at slot creation time

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SlotStatus status = SlotStatus.AVAILABLE;
}