package com.turfzy.booking;

import com.turfzy.booking.dto.BookingDto;
import com.turfzy.booking.dto.BookingRequest;
import com.turfzy.common.RedisLockService;
import com.turfzy.turf.SlotStatus;
import com.turfzy.user.User;
import com.turfzy.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private static final int CANCELLATION_HOURS_BEFORE = 2;

    private final BookingRepository bookingRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final UserRepository userRepository;
    private final RedisLockService redisLockService;
    private final BookingTransactionService bookingTransactionService;

    public BookingService(BookingRepository bookingRepository,
                          TimeSlotRepository timeSlotRepository,
                          UserRepository userRepository,
                          RedisLockService redisLockService,
                          BookingTransactionService bookingTransactionService) {
        this.bookingRepository = bookingRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.userRepository = userRepository;
        this.redisLockService = redisLockService;
        this.bookingTransactionService = bookingTransactionService;
    }

    // ─── CREATE BOOKING ──────────────────────────────────────────────────

    public BookingDto createBooking(BookingRequest request, Long customerId) {
        Long slotId = request.getSlotId();
        log.info("Booking attempt: slotId={}, customerId={}", slotId, customerId);

        // Pre-flight check — fast, no lock needed
        TimeSlot preCheck = timeSlotRepository.findById(slotId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Slot not found: " + slotId));

        if (preCheck.getStatus() != SlotStatus.AVAILABLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Slot is not available. Current status: " + preCheck.getStatus());
        }

        // Check slot is not in the past
        LocalDateTime slotDateTime = LocalDateTime.of(
                preCheck.getSlotDate(), preCheck.getStartTime());
        if (slotDateTime.isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot book a slot in the past");
        }

        // Layer 3: Redis distributed lock — one thread at a time per slot
        // bookingTransactionService.executeBookingInTransaction() is called
        // on a DIFFERENT Spring bean so @Transactional proxy works correctly
        return redisLockService.executeWithSlotLock(slotId,
                () -> bookingTransactionService.executeBookingInTransaction(slotId, customerId));
    }

    // ─── CANCEL BOOKING ──────────────────────────────────────────────────

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
                    "Only CONFIRMED bookings can be cancelled. Status: "
                            + booking.getStatus());
        }

        // Check cancellation window
        LocalDateTime slotStart = LocalDateTime.of(
                booking.getTimeSlot().getSlotDate(),
                booking.getTimeSlot().getStartTime());

        LocalDateTime cancelDeadline = slotStart.minusHours(CANCELLATION_HOURS_BEFORE);
        if (LocalDateTime.now().isAfter(cancelDeadline)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cancellation window has passed. Must cancel at least "
                            + CANCELLATION_HOURS_BEFORE + " hours before the slot.");
        }

        // Cancel booking
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(LocalDateTime.now());
        booking.setCancellationReason(reason != null ? reason : "Cancelled by customer");

        // Release slot back to AVAILABLE
        TimeSlot slot = booking.getTimeSlot();
        slot.setStatus(SlotStatus.AVAILABLE);
        timeSlotRepository.save(slot);

        Booking saved = bookingRepository.save(booking);
        log.info("Booking cancelled: ref={}, customerId={}",
                saved.getBookingReference(), customerId);

        return bookingTransactionService.toBookingDto(saved);
    }

    // ─── READ OPERATIONS ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<BookingDto> getMyBookings(Long customerId, int page, int size) {
        Pageable pageable = PageRequest.of(
                page, size, Sort.by("createdAt").descending());
        return bookingRepository.findByUserIdWithDetails(customerId, pageable)
                .map(bookingTransactionService::toBookingDto);
    }

    @Transactional(readOnly = true)
    public BookingDto getBookingDetail(Long bookingId, Long customerId) {
        Booking booking = bookingRepository.findByIdWithDetails(bookingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found: " + bookingId));

        if (!booking.getUser().getId().equals(customerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only view your own bookings");
        }

        return bookingTransactionService.toBookingDto(booking);
    }

    @Transactional(readOnly = true)
    public BookingDto getBookingByReference(String reference) {
        return bookingRepository.findByBookingReference(reference)
                .map(bookingTransactionService::toBookingDto)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found: " + reference));
    }

    @Transactional(readOnly = true)
    public Page<BookingDto> getBookingsForOwner(Long ownerId, int page, int size) {
        Pageable pageable = PageRequest.of(
                page, size, Sort.by("createdAt").descending());
        return bookingRepository.findByTurfOwnerIdWithDetails(ownerId, pageable)
                .map(bookingTransactionService::toBookingDto);
    }
}