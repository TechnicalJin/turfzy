package com.turfzy.booking;

import com.turfzy.turf.SlotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {

    /** Fetch all slots for a turf on a date (used in slot availability page) */
    List<TimeSlot> findByTurfIdAndSlotDateOrderByStartTimeAsc(
        Long turfId, LocalDate date);

    /** Fetch available slots only */
    List<TimeSlot> findByTurfIdAndSlotDateAndStatusOrderByStartTimeAsc(
        Long turfId, LocalDate date, SlotStatus status);

    /**
     * PESSIMISTIC WRITE LOCK on slot fetch.
     * Layer 2 of race condition prevention — used in BookingService.
     * When we lock this row, concurrent transactions must WAIT until
     * this transaction commits or rolls back.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM TimeSlot s WHERE s.id = :id")
    Optional<TimeSlot> findByIdWithLock(@Param("id") Long id);

    /** Check if slot exists (for duplicate prevention in slot generation) */
    boolean existsByTurfIdAndSlotDateAndStartTime(
        Long turfId, LocalDate date, java.time.LocalTime startTime);

    /** Count available slots for a turf in the next N days (owner dashboard) */
    @Query("""
        SELECT COUNT(s) FROM TimeSlot s
        WHERE s.turf.id = :turfId
        AND s.slotDate >= CURRENT_DATE
        AND s.status = 'AVAILABLE'
        """)
    long countUpcomingAvailableSlots(@Param("turfId") Long turfId);
}