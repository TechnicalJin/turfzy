package com.turfzy.booking;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    Page<Booking> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<Booking> findByBookingReference(String bookingReference);

    Optional<Booking> findByTimeSlotId(Long slotId);

    /** Owner dashboard — bookings for their turfs */
    @Query("""
        SELECT b FROM Booking b
        JOIN b.timeSlot s
        JOIN s.turf t
        WHERE t.owner.id = :ownerId
        ORDER BY b.createdAt DESC
        """)
    Page<Booking> findByTurfOwnerId(@Param("ownerId") Long ownerId, Pageable pageable);

    /** Revenue for an owner (CONFIRMED + REFUNDED bookings excluded from revenue) */
    @Query("""
        SELECT COALESCE(SUM(b.totalAmount), 0) FROM Booking b
        JOIN b.timeSlot s
        JOIN s.turf t
        WHERE t.owner.id = :ownerId
        AND b.status = 'CONFIRMED'
        """)
    java.math.BigDecimal getTotalRevenueByOwner(@Param("ownerId") Long ownerId);

    /** Admin dashboard — bookings in a date range */
    @Query("""
        SELECT b FROM Booking b
        JOIN b.timeSlot s
        WHERE s.slotDate BETWEEN :from AND :to
        ORDER BY b.createdAt DESC
        """)
    List<Booking> findBookingsBetweenDates(
        @Param("from") LocalDate from,
        @Param("to") LocalDate to);
}