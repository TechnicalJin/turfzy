package com.turfzy.booking;

import com.turfzy.booking.dto.BookingDto;
import com.turfzy.booking.dto.BookingRequest;
import com.turfzy.common.RedisLockService;
import com.turfzy.turf.SlotStatus;
import com.turfzy.user.User;
import com.turfzy.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * BookingService — handles the full booking lifecycle.
 *
 * 3-Layer Race Condition Prevention:
 * ┌─────────────────────────────────────────────────────────────┐
 * │ Layer 3: Redis distributed lock (slot_lock:{slotId})        │
 * │   ↓ acquired — only one thread cluster-wide proceeds        │
 * │ Layer 2: DB Pessimistic Write Lock (SELECT FOR UPDATE)      │
 * │   ↓ acquired — only one DB transaction proceeds             │
 * │ Layer 1: DB UNIQUE constraint (turf_id, slot_date, start)   │
 * │   ↓ hard stop — rejects duplicate even if layers 2&3 fail   │
 * └─────────────────────────────────────────────────────────────┘
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private static final int CANCELLATION_HOURS_BEFORE = 2;

    // Daily counter for booking reference generation (resets on restart — fine for MVP)
    private final AtomicInteger dailyCounter = new AtomicInteger(0);

    private final BookingRepository bookingRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final UserRepository userRepository;
    private final RedisLockService redisLockService;

    public BookingService(BookingRepository bookingRepository,
                          TimeSlotRepository timeSlotRepository,
                          UserRepository userRepository,
                          RedisLockService redisLockService) {
        this.bookingRepository = bookingRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.userRepository = userRepository;
        this.redisLockService = redisLockService;
    }

    // ─── CREATE BOOKING ──────────────────────────────────────────────────

    /**
     * Creates a booking for a slot with full 3-layer race condition protection.
     *
     * Flow:
     * 1. Validate slot exists and is AVAILABLE (fast check, no lock)
     * 2. Acquire Redis distributed lock on slot_lock:{slotId}
     * 3. Inside lock: start DB transaction with SERIALIZABLE isolation
     * 4. Re-fetch slot with PESSIMISTIC_WRITE lock (SELECT FOR UPDATE)
     * 5. Re-validate status (may have changed between step 1 and 4)
     * 6. Mark slot as BOOKED + create Booking record
     * 7. Release DB lock (transaction commits) → release Redis lock
     *
     * Why re-validate in step 5?
     * Between step 1 (initial check) and step 4 (locked fetch), another
     * thread may have booked the slot. The pessimistic lock ensures we
     * see the committed state, not a stale read.
     */
    public BookingDto createBooking(BookingRequest request, Long customerId) {
        Long slotId = request.getSlotId();
        log.info("Booking attempt: slotId={}, customerId={}", slotId, customerId);

        // Pre-flight check (no lock needed — just fast validation)
        TimeSlot preCheck = timeSlotRepository.findById(slotId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Slot not found: " + slotId));

        if (preCheck.getStatus() != SlotStatus.AVAILABLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Slot is not available. Status: " + preCheck.getStatus());
        }

        // Validate slot is not in the past
        LocalDateTime slotDateTime = LocalDateTime.of(
            preCheck.getSlotDate(), preCheck.getStartTime());
        if (slotDateTime.isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Cannot book a slot in the past");
        }

        // Layer 3: Acquire Redis distributed lock
        return redisLockService.executeWithSlotLock(slotId, () ->
            executeBookingTransaction(slotId, customerId));
    }

    /**
     * The actual DB transaction — runs inside the Redis lock.
     * Uses SERIALIZABLE isolation for maximum safety.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    protected BookingDto executeBookingTransaction(Long slotId, Long customerId) {
        try {
            // Layer 2: Pessimistic Write Lock — SELECT FOR UPDATE
            TimeSlot slot = timeSlotRepository.findByIdWithLock(slotId)
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Slot not found"));

            // Re-validate AFTER acquiring lock — critical double-check
            if (slot.getStatus() != SlotStatus.AVAILABLE) {
                log.warn("Slot {} was grabbed by another thread between pre-check and lock acquisition", slotId);
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Slot was just booked by someone else. Please choose another slot.");
            }

            User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Customer not found"));

            // Mark slot as BOOKED
            slot.setStatus(SlotStatus.BOOKED);
            timeSlotRepository.save(slot);

            // Create booking record
            Booking booking = Booking.builder()
                .user(customer)
                .timeSlot(slot)
                .status(BookingStatus.CONFIRMED)
                .totalAmount(slot.getPrice())
                .bookingReference(generateBookingReference())
                .build();

            Booking saved = bookingRepository.save(booking);
            log.info("Booking created: ref={}, slotId={}, customerId={}",
                saved.getBookingReference(), slotId, customerId);

            return toBookingDto(saved);

        } catch (DataIntegrityViolationException e) {
            // Layer 1: DB unique constraint fired — absolute last resort
            log.error("DB unique constraint violation for slotId={} — slot double-booked at DB level", slotId);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Slot was already booked. This is a concurrent booking conflict.");
        }
    }

    // ─── CANCEL BOOKING ──────────────────────────────────────────────────

    /**
     * Cancels a CONFIRMED booking.
     *
     * Rules:
     * - Only the booking owner can cancel (PBAC)
     * - Must be at least CANCELLATION_HOURS_BEFORE the slot start
     * - Cancellation releases the slot back to AVAILABLE
     *
     * Note: Payment refund is triggered separately on Day 8 (Razorpay).
     * For now, we just mark booking as CANCELLED and free the slot.
     */
    @Transactional
    public BookingDto cancelBooking(Long bookingId, Long customerId, String reason) {
        Booking booking = bookingRepository.findByIdWithDetails(bookingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found: " + bookingId));

        // PBAC: only the booking owner can cancel
        if (!booking.getUser().getId().equals(customerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "You can only cancel your own bookings");
        }

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Only CONFIRMED bookings can be cancelled. Current status: "
                    + booking.getStatus());
        }

        // Check cancellation window
        LocalDateTime slotStart = LocalDateTime.of(
            booking.getTimeSlot().getSlotDate(),
            booking.getTimeSlot().getStartTime());

        LocalDateTime cancelDeadline = slotStart.minusHours(CANCELLATION_HOURS_BEFORE);

        if (LocalDateTime.now().isAfter(cancelDeadline)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Cancellation window has passed. Bookings must be cancelled at least "
                    + CANCELLATION_HOURS_BEFORE + " hours before the slot.");
        }

        // Update booking
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(LocalDateTime.now());
        booking.setCancellationReason(reason != null ? reason : "Cancelled by customer");

        // Release the slot back to AVAILABLE
        TimeSlot slot = booking.getTimeSlot();
        slot.setStatus(SlotStatus.AVAILABLE);
        timeSlotRepository.save(slot);

        Booking saved = bookingRepository.save(booking);
        log.info("Booking cancelled: ref={}, customerId={}, reason={}",
            saved.getBookingReference(), customerId, reason);

        return toBookingDto(saved);
    }

    // ─── READ OPERATIONS ─────────────────────────────────────────────────

    /** Customer's booking history — paginated */
    @Transactional(readOnly = true)
    public Page<BookingDto> getMyBookings(Long customerId, int page, int size) {
        Pageable pageable = PageRequest.of(
            page, size, Sort.by("createdAt").descending());
        return bookingRepository.findByUserIdWithDetails(customerId, pageable)
                .map(this::toBookingDto);
    }

    /** Single booking detail */
    @Transactional(readOnly = true)
    public BookingDto getBookingDetail(Long bookingId, Long customerId) {
        Booking booking = bookingRepository.findByIdWithDetails(bookingId)
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Booking not found"));

        // PBAC: customer can only see their own bookings
        if (!booking.getUser().getId().equals(customerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "You can only view your own bookings");
        }

        return toBookingDto(booking);
    }

    /** Get booking by reference number */
    @Transactional(readOnly = true)
    public BookingDto getBookingByReference(String reference) {
        return bookingRepository.findByBookingReference(reference)
            .map(this::toBookingDto)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Booking not found: " + reference));
    }

    /** Owner views bookings for their turfs */
    @Transactional(readOnly = true)
    public Page<BookingDto> getBookingsForOwner(Long ownerId, int page, int size) {
        Pageable pageable = PageRequest.of(
            page, size, Sort.by("createdAt").descending());
        return bookingRepository.findByTurfOwnerIdWithDetails(ownerId, pageable)
                .map(this::toBookingDto);
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────

    /**
     * Generates a human-readable booking reference.
     * Format: TRZ-YYYYMMDD-XXXX (e.g., TRZ-20260413-0042)
     *
     * AtomicInteger ensures thread-safe increment.
     * Resets on app restart — acceptable for MVP.
     * Production: use DB sequence or UUID prefix.
     */
    private String generateBookingReference() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int counter = dailyCounter.incrementAndGet();
        return String.format("TRZ-%s-%04d", date, counter);
    }

    private BookingDto toBookingDto(Booking booking) {
        TimeSlot slot = booking.getTimeSlot();
        User customer = booking.getUser();

        return BookingDto.builder()
            .id(booking.getId())
            .bookingReference(booking.getBookingReference())
            .status(booking.getStatus())
            .totalAmount(booking.getTotalAmount())
            .createdAt(booking.getCreatedAt())
            .cancelledAt(booking.getCancelledAt())
            .cancellationReason(booking.getCancellationReason())
            // Slot details
            .slotId(slot.getId())
            .slotDate(slot.getSlotDate())
            .startTime(slot.getStartTime())
            .endTime(slot.getEndTime())
            // Turf details
            .turfId(slot.getTurf().getId())
            .turfName(slot.getTurf().getName())
            .turfCity(slot.getTurf().getCity())
            .turfAddress(slot.getTurf().getAddress())
            // Customer details
            .customerId(customer.getId())
            .customerName(customer.getName())
            .customerEmail(customer.getEmail())
            .build();
    }
}