package com.turfzy.booking;

import com.turfzy.booking.dto.SlotDto;
import com.turfzy.common.ApiResponse;
import com.turfzy.turf.SlotStatus;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Slot REST Controller.
 *
 * Routes:
 * GET  /api/slots/{turfId}?date=2024-12-15  → public: all slots for a turf+date
 * GET  /api/slots/{turfId}/available?date=  → public: only available slots
 * POST /api/owner/slots/{slotId}/block      → owner blocks a slot
 * POST /api/owner/slots/{slotId}/unblock    → owner unblocks a slot
 * POST /api/owner/turfs/{turfId}/generate-slots → owner manually triggers generation
 */
@RestController
public class SlotController {

    private static final Logger log = LoggerFactory.getLogger(SlotController.class);

    private final TimeSlotRepository timeSlotRepository;
    private final SlotGenerationService slotGenerationService;
    private final com.turfzy.turf.TurfRepository turfRepository;
    private final com.turfzy.user.UserRepository userRepository;

    public SlotController(TimeSlotRepository timeSlotRepository,
                          SlotGenerationService slotGenerationService,
                          com.turfzy.turf.TurfRepository turfRepository,
                          com.turfzy.user.UserRepository userRepository) {
        this.timeSlotRepository = timeSlotRepository;
        this.slotGenerationService = slotGenerationService;
        this.turfRepository = turfRepository;
        this.userRepository = userRepository;
    }

    // ─── PUBLIC ──────────────────────────────────────────────────────────

    /**
     * Returns ALL slots for a turf on a date (including BOOKED/BLOCKED).
     * Frontend uses status field to show booked slots as greyed out.
     * Defaults to today if no date provided.
     */
    @GetMapping("/api/slots/{turfId}")
    public ResponseEntity<ApiResponse<List<SlotDto>>> getSlotsForDate(
            @PathVariable Long turfId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate queryDate = (date != null) ? date : LocalDate.now();
        log.debug("Fetching slots for turfId={}, date={}", turfId, queryDate);

        // Validate turf exists
        if (!turfRepository.existsById(turfId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Turf not found: " + turfId);
        }

        List<TimeSlot> slots = timeSlotRepository
            .findByTurfIdAndSlotDateOrderByStartTimeAsc(turfId, queryDate);

        List<SlotDto> dtos = slots.stream()
            .map(this::toSlotDto)
            .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(
            "Slots fetched for " + queryDate, dtos));
    }

    /**
     * Returns ONLY available slots for a date range.
     * Used by the booking calendar — customers see green (available) slots only.
     */
    @GetMapping("/api/slots/{turfId}/available")
    public ResponseEntity<ApiResponse<List<SlotDto>>> getAvailableSlots(
            @PathVariable Long turfId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate queryDate = (date != null) ? date : LocalDate.now();

        List<TimeSlot> slots = timeSlotRepository
            .findByTurfIdAndSlotDateAndStatusOrderByStartTimeAsc(
                turfId, queryDate, SlotStatus.AVAILABLE);

        List<SlotDto> dtos = slots.stream()
            .map(this::toSlotDto)
            .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(
            "Available slots fetched", dtos));
    }

    /**
     * Returns slots for a 7-day window starting from a date.
     * Used to render the weekly availability calendar on the turf detail page.
     */
    @GetMapping("/api/slots/{turfId}/week")
    public ResponseEntity<ApiResponse<java.util.Map<String, List<SlotDto>>>> getWeekSlots(
            @PathVariable Long turfId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate) {

        LocalDate start = (startDate != null) ? startDate : LocalDate.now();

        java.util.Map<String, List<SlotDto>> weekSlots = new java.util.LinkedHashMap<>();

        for (int i = 0; i < 7; i++) {
            LocalDate day = start.plusDays(i);
            List<SlotDto> daySlots = timeSlotRepository
                .findByTurfIdAndSlotDateOrderByStartTimeAsc(turfId, day)
                .stream()
                .map(this::toSlotDto)
                .collect(Collectors.toList());
            weekSlots.put(day.toString(), daySlots);
        }

        return ResponseEntity.ok(ApiResponse.success("Week slots fetched", weekSlots));
    }

    // ─── OWNER ───────────────────────────────────────────────────────────

    /**
     * Owner blocks a specific slot.
     * Uses findByIdWithTurfAndOwner — eagerly loads turf+owner in one query
     * to avoid LazyInitializationException during ownership check.
     */
    @PostMapping("/api/owner/slots/{slotId}/block")
    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public ResponseEntity<ApiResponse<SlotDto>> blockSlot(
            @PathVariable Long slotId,
            @AuthenticationPrincipal UserDetails userDetails) {

        TimeSlot slot = timeSlotRepository.findByIdWithTurfAndOwner(slotId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Slot not found: " + slotId));

        assertSlotOwnership(slot, userDetails.getUsername());

        if (slot.getStatus() == SlotStatus.BOOKED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot block a slot that is already booked");
        }
        if (slot.getStatus() == SlotStatus.BLOCKED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Slot is already blocked");
        }

        slot.setStatus(SlotStatus.BLOCKED);
        TimeSlot saved = timeSlotRepository.save(slot);
        log.info("Slot blocked: id={} by owner: {}", slotId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Slot blocked", toSlotDto(saved)));
    }

    @PostMapping("/api/owner/slots/{slotId}/unblock")
    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public ResponseEntity<ApiResponse<SlotDto>> unblockSlot(
            @PathVariable Long slotId,
            @AuthenticationPrincipal UserDetails userDetails) {

        TimeSlot slot = timeSlotRepository.findByIdWithTurfAndOwner(slotId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Slot not found: " + slotId));

        assertSlotOwnership(slot, userDetails.getUsername());

        if (slot.getStatus() != SlotStatus.BLOCKED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Slot is not blocked — cannot unblock");
        }

        slot.setStatus(SlotStatus.AVAILABLE);
        TimeSlot saved = timeSlotRepository.save(slot);
        log.info("Slot unblocked: id={} by owner: {}", slotId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Slot unblocked", toSlotDto(saved)));
    }

    // Replace the old findSlotOrThrow — it's no longer used directly for block/unblock
// Keep it for other potential usages but it's no longer called above
    private TimeSlot findSlotOrThrow(Long slotId) {
        return timeSlotRepository.findById(slotId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Slot not found: " + slotId));
    }

    /**
     * Owner manually triggers slot generation for their turf.
     * Useful if the cron job hasn't run yet or after opening hours change.
     */
    @PostMapping("/api/owner/turfs/{turfId}/generate-slots")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<String>> manuallyGenerateSlots(
            @PathVariable Long turfId,
            @AuthenticationPrincipal UserDetails userDetails) {

        com.turfzy.turf.Turf turf = turfRepository.findById(turfId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Turf not found"));

        // Verify ownership
        Long ownerId = resolveUserId(userDetails.getUsername());
        if (!turf.getOwner().getId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "You do not own this turf");
        }

        if (turf.getStatus() != com.turfzy.turf.TurfStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Turf must be ACTIVE to generate slots");
        }

        slotGenerationService.generateSlotsForNewlyApprovedTurf(turfId);

        return ResponseEntity.ok(ApiResponse.success(
            "Slot generation triggered for turf: " + turf.getName()));
    }


    /**
     * Ensures the requesting owner actually owns the turf this slot belongs to.
     */
    private void assertSlotOwnership(TimeSlot slot, String ownerEmail) {
        String turfOwnerEmail = slot.getTurf().getOwner().getEmail();
        if (!turfOwnerEmail.equals(ownerEmail)) {
            log.warn("Ownership violation on slot: slotId={}, requestedBy={}",
                slot.getId(), ownerEmail);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "You do not own the turf this slot belongs to");
        }
    }

    private Long resolveUserId(String email) {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "User not found"))
            .getId();
    }

    private SlotDto toSlotDto(TimeSlot slot) {
        return SlotDto.builder()
            .id(slot.getId())
            .slotDate(slot.getSlotDate())
            .startTime(slot.getStartTime())
            .endTime(slot.getEndTime())
            .price(slot.getPrice())
            .status(slot.getStatus())
            .available(slot.getStatus() == SlotStatus.AVAILABLE)
            .build();
    }
}