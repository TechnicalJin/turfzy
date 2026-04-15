package com.turfzy.booking;

import com.turfzy.booking.dto.BookingDto;
import com.turfzy.turf.SlotStatus;
import com.turfzy.user.User;
import com.turfzy.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Separated into its own Spring bean specifically so that
 * @Transactional works correctly when called from inside a lambda.
 *
 * WHY A SEPARATE CLASS?
 * Spring @Transactional works via AOP proxy — the proxy wraps method calls
 * made FROM OUTSIDE the bean. When BookingService called
 * executeBookingTransaction() from inside a lambda, the call was
 * self-invocation (same bean instance), bypassing the proxy entirely.
 * Moving it here means BookingService calls it on a DIFFERENT proxy bean,
 * so @Transactional is properly intercepted and a transaction is started.
 */
@Service
public class BookingTransactionService {

    private static final Logger log =
        LoggerFactory.getLogger(BookingTransactionService.class);

    private final AtomicInteger dailyCounter = new AtomicInteger(0);

    private final BookingRepository bookingRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final UserRepository userRepository;

    public BookingTransactionService(BookingRepository bookingRepository,
                                     TimeSlotRepository timeSlotRepository,
                                     UserRepository userRepository) {
        this.bookingRepository = bookingRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.userRepository = userRepository;
    }

    /**
     * Executes the actual booking inside a DB transaction.
     * Called from BookingService AFTER the Redis lock is acquired.
     *
     * Transaction lifecycle:
     * 1. @Transactional opens a DB connection + starts transaction
     * 2. findByIdWithLock() → SELECT ... FOR UPDATE (Layer 2 lock)
     * 3. Re-validate slot is still AVAILABLE
     * 4. slot.status = BOOKED → save
     * 5. Booking record created → save
     * 6. @Transactional commits → DB lock released
     *
     * Isolation.SERIALIZABLE prevents phantom reads — if two transactions
     * both pass the AVAILABLE check before either commits, SERIALIZABLE
     * ensures only one can proceed.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BookingDto executeBookingInTransaction(Long slotId, Long customerId) {
        try {
            // Layer 2: SELECT ... FOR UPDATE — blocks concurrent transactions
            TimeSlot slot = timeSlotRepository.findByIdWithLock(slotId)
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Slot not found: " + slotId));

            // Re-validate AFTER acquiring lock — critical double-check
            // Status may have changed between pre-check and now
            if (slot.getStatus() != SlotStatus.AVAILABLE) {
                log.warn("Slot {} grabbed between pre-check and lock — status: {}",
                    slotId, slot.getStatus());
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Slot was just booked by someone else. Please choose another slot.");
            }

            User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Customer not found: " + customerId));

            // Mark slot as BOOKED
            slot.setStatus(SlotStatus.BOOKED);
            timeSlotRepository.save(slot);

            // Create the booking record
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
            // Layer 1: DB UNIQUE constraint fired — absolute last resort
            log.error("DB unique constraint violation for slotId={}", slotId);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Slot was already booked. Please choose another slot.");
        }
    }

    private String generateBookingReference() {
        String date = LocalDate.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int counter = dailyCounter.incrementAndGet();
        return String.format("TRZ-%s-%04d", date, counter);
    }

    public BookingDto toBookingDto(Booking booking) {
        TimeSlot slot = booking.getTimeSlot();
        User customer = booking.getUser();

        return com.turfzy.booking.dto.BookingDto.builder()
            .id(booking.getId())
            .bookingReference(booking.getBookingReference())
            .status(booking.getStatus())
            .totalAmount(booking.getTotalAmount())
            .createdAt(booking.getCreatedAt())
            .cancelledAt(booking.getCancelledAt())
            .cancellationReason(booking.getCancellationReason())
            .slotId(slot.getId())
            .slotDate(slot.getSlotDate())
            .startTime(slot.getStartTime())
            .endTime(slot.getEndTime())
            .turfId(slot.getTurf().getId())
            .turfName(slot.getTurf().getName())
            .turfCity(slot.getTurf().getCity())
            .turfAddress(slot.getTurf().getAddress())
            .customerId(customer.getId())
            .customerName(customer.getName())
            .customerEmail(customer.getEmail())
            .build();
    }
}