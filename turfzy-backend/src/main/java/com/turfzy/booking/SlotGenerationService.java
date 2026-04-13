package com.turfzy.booking;

import com.turfzy.turf.SlotStatus;
import com.turfzy.turf.Turf;
import com.turfzy.turf.TurfRepository;
import com.turfzy.turf.TurfStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class SlotGenerationService {

    private static final Logger log = LoggerFactory.getLogger(SlotGenerationService.class);
    private static final int GENERATION_DAYS_AHEAD = 30;

    private final TimeSlotRepository timeSlotRepository;
    private final TurfRepository turfRepository;

    public SlotGenerationService(TimeSlotRepository timeSlotRepository,
                                 TurfRepository turfRepository) {
        this.timeSlotRepository = timeSlotRepository;
        this.turfRepository = turfRepository;
    }

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void scheduledSlotGeneration() {
        log.info("=== Scheduled slot generation started ===");
        List<Turf> activeTurfs = turfRepository.findByStatusOrderByCreatedAtAsc(TurfStatus.ACTIVE);
        log.info("Generating slots for {} active turfs", activeTurfs.size());

        int totalGenerated = 0;
        for (Turf turf : activeTurfs) {
            int generated = generateSlotsForTurf(turf, LocalDate.now(), GENERATION_DAYS_AHEAD);
            totalGenerated += generated;
        }
        log.info("=== Scheduled generation complete — {} new slots created ===", totalGenerated);
    }

    /**
     * Called after admin approves a turf.
     *
     * REQUIRES_NEW — runs in its own transaction AFTER the caller's transaction
     * (approveTurf) has committed. This ensures the turf with status=ACTIVE
     * is visible in the DB when we fetch it here.
     *
     * Without REQUIRES_NEW, both run in the same transaction and the turf
     * save may not be flushed yet when this method reads it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generateSlotsForNewlyApprovedTurf(Long turfId) {
        log.info("Generating initial slots for newly approved turfId={}", turfId);

        Turf turf = turfRepository.findById(turfId)
                .orElseThrow(() -> new IllegalArgumentException("Turf not found: " + turfId));

        int generated = generateSlotsForTurf(turf, LocalDate.now(), GENERATION_DAYS_AHEAD);
        log.info("Generated {} slots for turfId={}", generated, turfId);
    }

    /**
     * Used by admin manual trigger endpoint.
     * Separate transaction so it always reads the latest committed turf state.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generateSlotsForTurfById(Long turfId) {
        Turf turf = turfRepository.findById(turfId)
                .orElseThrow(() -> new IllegalArgumentException("Turf not found: " + turfId));

        log.info("Manual slot generation for turfId={}", turfId);
        int generated = generateSlotsForTurf(turf, LocalDate.now(), GENERATION_DAYS_AHEAD);
        log.info("Manual generation complete: {} slots for turfId={}", generated, turfId);
    }

    @Transactional
    public int generateSlotsForTurf(Turf turf, LocalDate startDate, int daysAhead) {
        int slotsCreated = 0;
        List<TimeSlot> slotsToSave = new ArrayList<>();

        LocalTime openingTime = turf.getOpeningTime();
        LocalTime closingTime = turf.getClosingTime();

        log.info("Generating slots for turfId={} | {} to {} | {} days from {}",
                turf.getId(), openingTime, closingTime, daysAhead, startDate);

        for (int day = 0; day < daysAhead; day++) {
            LocalDate slotDate = startDate.plusDays(day);
            LocalTime currentSlotTime = openingTime;

            while (currentSlotTime.plusHours(1).compareTo(closingTime) <= 0) {
                LocalTime slotStart = currentSlotTime;
                LocalTime slotEnd   = currentSlotTime.plusHours(1);

                boolean exists = timeSlotRepository
                        .existsByTurfIdAndSlotDateAndStartTime(
                                turf.getId(), slotDate, slotStart);

                if (!exists) {
                    slotsToSave.add(TimeSlot.builder()
                            .turf(turf)
                            .slotDate(slotDate)
                            .startTime(slotStart)
                            .endTime(slotEnd)
                            .price(turf.getPricePerHour())
                            .status(SlotStatus.AVAILABLE)
                            .build());
                    slotsCreated++;
                }

                currentSlotTime = currentSlotTime.plusHours(1);
            }
        }

        if (!slotsToSave.isEmpty()) {
            timeSlotRepository.saveAll(slotsToSave);
            log.info("Saved {} new slots for turfId={}", slotsToSave.size(), turf.getId());
        } else {
            log.info("No new slots needed for turfId={} (all already exist)", turf.getId());
        }

        return slotsCreated;
    }

    @Transactional
    public void regenerateSlotsForDate(Long turfId, LocalDate date) {
        Turf turf = turfRepository.findById(turfId)
                .orElseThrow(() -> new IllegalArgumentException("Turf not found: " + turfId));

        List<TimeSlot> existingAvailable = timeSlotRepository
                .findByTurfIdAndSlotDateAndStatusOrderByStartTimeAsc(
                        turfId, date, SlotStatus.AVAILABLE);

        if (!existingAvailable.isEmpty()) {
            timeSlotRepository.deleteAll(existingAvailable);
            log.info("Deleted {} available slots for turfId={} on {}",
                    existingAvailable.size(), turfId, date);
        }

        generateSlotsForTurf(turf, date, 1);
    }
}