package com.turfzy.booking;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    /**
     * Reference lookup with full JOIN FETCH — avoids LazyInitializationException
     * when toBookingDto() accesses slot.getTurf() outside a transaction.
     */
    @Query("""
        SELECT b FROM Booking b
        JOIN FETCH b.user
        JOIN FETCH b.timeSlot s
        JOIN FETCH s.turf
        WHERE b.bookingReference = :reference
        """)
    Optional<Booking> findByBookingReference(@Param("reference") String reference);

    @Query("""
        SELECT b FROM Booking b
        JOIN FETCH b.user
        JOIN FETCH b.timeSlot s
        JOIN FETCH s.turf
        WHERE b.id = :id
        """)
    Optional<Booking> findByIdWithDetails(@Param("id") Long id);

    @Query("""
        SELECT b FROM Booking b
        JOIN FETCH b.user
        JOIN FETCH b.timeSlot s
        JOIN FETCH s.turf
        WHERE b.user.id = :userId
        ORDER BY b.createdAt DESC
        """)
    Page<Booking> findByUserIdWithDetails(
            @Param("userId") Long userId, Pageable pageable);

    @Query("""
        SELECT b FROM Booking b
        JOIN FETCH b.user
        JOIN FETCH b.timeSlot s
        JOIN FETCH s.turf t
        WHERE t.owner.id = :ownerId
        ORDER BY b.createdAt DESC
        """)
    Page<Booking> findByTurfOwnerIdWithDetails(
            @Param("ownerId") Long ownerId, Pageable pageable);

    @Query("""
        SELECT COALESCE(SUM(b.totalAmount), 0) FROM Booking b
        JOIN b.timeSlot s
        JOIN s.turf t
        WHERE t.owner.id = :ownerId
        AND b.status = 'CONFIRMED'
        """)
    java.math.BigDecimal getTotalRevenueByOwner(@Param("ownerId") Long ownerId);

    Page<Booking> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}