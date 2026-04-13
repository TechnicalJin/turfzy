package com.turfzy.booking;

import com.turfzy.booking.dto.BookingDto;
import com.turfzy.booking.dto.BookingRequest;
import com.turfzy.common.ApiResponse;
import com.turfzy.common.RedisLockService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Booking REST Controller.
 *
 * Routes:
 * POST /api/bookings                     → customer creates booking
 * GET  /api/bookings                     → customer's booking history
 * GET  /api/bookings/{id}                → booking detail
 * GET  /api/bookings/reference/{ref}     → lookup by reference
 * POST /api/bookings/{id}/cancel         → customer cancels
 * GET  /api/owner/bookings               → owner sees bookings on their turfs
 */
@RestController
public class BookingController {

    private static final Logger log = LoggerFactory.getLogger(BookingController.class);

    private final BookingService bookingService;
    private final com.turfzy.user.UserRepository userRepository;

    public BookingController(BookingService bookingService,
                             com.turfzy.user.UserRepository userRepository) {
        this.bookingService = bookingService;
        this.userRepository = userRepository;
    }

    // ─── CUSTOMER ────────────────────────────────────────────────────────

    @PostMapping("/api/bookings")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<BookingDto>> createBooking(
            @Valid @RequestBody BookingRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long customerId = resolveUserId(userDetails);
        log.info("POST /api/bookings — slotId={}, customerId={}",
            request.getSlotId(), customerId);

        try {
            BookingDto booking = bookingService.createBooking(request, customerId);
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Booking confirmed!", booking));

        } catch (RedisLockService.SlotLockException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/api/bookings")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Page<BookingDto>>> getMyBookings(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long customerId = resolveUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success("Bookings fetched",
            bookingService.getMyBookings(customerId, page, size)));
    }

    @GetMapping("/api/bookings/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<BookingDto>> getBookingDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long customerId = resolveUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success("Booking detail fetched",
            bookingService.getBookingDetail(id, customerId)));
    }

    @GetMapping("/api/bookings/reference/{ref}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<BookingDto>> getByReference(
            @PathVariable String ref) {
        return ResponseEntity.ok(ApiResponse.success("Booking found",
            bookingService.getBookingByReference(ref)));
    }

    @PostMapping("/api/bookings/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<BookingDto>> cancelBooking(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long customerId = resolveUserId(userDetails);
        String reason = (body != null) ? body.get("reason") : null;

        log.info("POST /api/bookings/{}/cancel — customerId={}", id, customerId);
        return ResponseEntity.ok(ApiResponse.success("Booking cancelled",
            bookingService.cancelBooking(id, customerId, reason)));
    }

    // ─── OWNER ───────────────────────────────────────────────────────────

    @GetMapping("/api/owner/bookings")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<Page<BookingDto>>> getOwnerBookings(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long ownerId = resolveUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success("Owner bookings fetched",
            bookingService.getBookingsForOwner(ownerId, page, size)));
    }

    // ─── HELPER ──────────────────────────────────────────────────────────

    private Long resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "User not found"))
            .getId();
    }
}