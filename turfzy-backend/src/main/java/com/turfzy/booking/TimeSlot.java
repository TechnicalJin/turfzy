package com.turfzy.booking;

import com.turfzy.common.BaseEntity;
import com.turfzy.turf.SlotStatus;
import com.turfzy.turf.Turf;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * TimeSlot — represents a 1-hour bookable window for a turf on a specific date.
 *
 * CRITICAL: The UNIQUE constraint on (turf_id, slot_date, start_time) is
 * Layer 1 of our 3-layer race condition prevention strategy.
 *
 * If two users try to book the same slot simultaneously and both pass
 * application-level checks, only one INSERT will succeed at the DB level.
 * The second will throw a DataIntegrityViolationException which we catch
 * and convert to a "Slot already booked" error.
 *
 * Slot generation: Slots are auto-generated for the next 30 days
 * by a @Scheduled job (Day 5) based on turf opening/closing hours.
 */
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