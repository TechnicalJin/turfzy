package com.turfzy.turf;

import com.turfzy.booking.SlotGenerationService;
import com.turfzy.common.CloudinaryService;
import com.turfzy.turf.dto.*;
import com.turfzy.user.User;
import com.turfzy.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TurfService {

    private static final Logger log = LoggerFactory.getLogger(TurfService.class);
    private static final int MAX_IMAGES_PER_TURF = 8;

    private final TurfRepository turfRepository;
    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService;

    private final SlotGenerationService slotGenerationService;


    public TurfService(TurfRepository turfRepository,
                       UserRepository userRepository,
                       CloudinaryService cloudinaryService,
                       SlotGenerationService slotGenerationService) {
        this.turfRepository = turfRepository;
        this.userRepository = userRepository;
        this.cloudinaryService = cloudinaryService;
        this.slotGenerationService = slotGenerationService;
    }

    // ─── PUBLIC ENDPOINTS ───────────────────────────────────────────────

    /** Paginated search for active turfs — used on homepage and search page */
    @Transactional(readOnly = true)
    public Page<TurfSummaryDto> searchTurfs(String city, SportType sport,
                                             int page, int size) {
        Pageable pageable = PageRequest.of(
            page, size, Sort.by("averageRating").descending());

        return turfRepository.searchTurfs(city, sport, pageable)
            .map(this::toSummaryDto);
    }

    /** Full turf detail — public, any status shown (owner/admin may view pending) */
    @Transactional(readOnly = true)
    public TurfDetailDto getTurfById(Long turfId) {
        Turf turf = findTurfOrThrow(turfId);
        return toDetailDto(turf);
    }

    // ─── OWNER ENDPOINTS ────────────────────────────────────────────────

    /**
     * Create a new turf listing.
     * Status starts as PENDING_APPROVAL — admin must approve before it goes live.
     */
    @Transactional
    @PreAuthorize("hasRole('OWNER')")
    public TurfDetailDto createTurf(TurfCreateRequest request, Long ownerId) {
        log.info("Creating turf for ownerId={}: name={}", ownerId, request.getName());

        User owner = userRepository.findById(ownerId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Owner not found"));

        Turf turf = Turf.builder()
            .name(request.getName())
            .description(request.getDescription())
            .address(request.getAddress())
            .city(request.getCity())
            .state(request.getState())
            .pincode(request.getPincode())
            .pricePerHour(request.getPricePerHour())
            .phone(request.getPhone())
            .latitude(request.getLatitude())
            .longitude(request.getLongitude())
            .sportTypes(request.getSportTypes())
            .status(TurfStatus.PENDING_APPROVAL)
                .openingTime(request.getOpeningTime() != null
                        ? request.getOpeningTime() : LocalTime.of(6, 0))
                .closingTime(request.getClosingTime() != null
                        ? request.getClosingTime() : LocalTime.of(22, 0))
            .owner(owner)
            .build();

        Turf saved = turfRepository.save(turf);
        log.info("Turf created: id={}, status=PENDING_APPROVAL", saved.getId());
        return toDetailDto(saved);
    }

    /**
     * Update turf details.
     * PBAC: only the owner of THIS turf can update it.
     */
    @Transactional
    @PreAuthorize("hasRole('OWNER')")
    public TurfDetailDto updateTurf(Long turfId, TurfUpdateRequest request, Long ownerId) {
        Turf turf = findTurfOrThrow(turfId);
        assertOwnership(turf, ownerId);

        // Partial update — only update non-null fields
        if (request.getName() != null)         turf.setName(request.getName());
        if (request.getDescription() != null)  turf.setDescription(request.getDescription());
        if (request.getAddress() != null)      turf.setAddress(request.getAddress());
        if (request.getCity() != null)         turf.setCity(request.getCity());
        if (request.getState() != null)        turf.setState(request.getState());
        if (request.getPincode() != null)      turf.setPincode(request.getPincode());
        if (request.getPricePerHour() != null) turf.setPricePerHour(request.getPricePerHour());
        if (request.getPhone() != null)        turf.setPhone(request.getPhone());
        if (request.getSportTypes() != null)   turf.setSportTypes(request.getSportTypes());
        if (request.getLatitude() != null)     turf.setLatitude(request.getLatitude());
        if (request.getLongitude() != null)    turf.setLongitude(request.getLongitude());
        if (request.getOpeningTime() != null) turf.setOpeningTime(request.getOpeningTime());
        if (request.getClosingTime() != null) turf.setClosingTime(request.getClosingTime());

        log.info("Turf updated: id={} by ownerId={}", turfId, ownerId);
        return toDetailDto(turfRepository.save(turf));
    }

    /**
     * Upload images for a turf.
     * Max 8 images per turf. First uploaded image becomes primary.
     *
     * IMPORTANT: If DB save fails after Cloudinary upload,
     * we delete the uploaded image to prevent storage leak.
     */
    @Transactional
    @PreAuthorize("hasRole('OWNER')")
    public TurfDetailDto uploadImages(Long turfId, List<MultipartFile> files, Long ownerId) {
        Turf turf = findTurfOrThrow(turfId);
        assertOwnership(turf, ownerId);

        int currentImageCount = turf.getImages().size();
        if (currentImageCount + files.size() > MAX_IMAGES_PER_TURF) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Maximum " + MAX_IMAGES_PER_TURF + " images allowed per turf. " +
                "Currently has " + currentImageCount + ".");
        }

        for (MultipartFile file : files) {
            Map<String, String> uploaded = null;
            try {
                uploaded = cloudinaryService.uploadTurfImage(file, turfId);

                TurfImage image = TurfImage.builder()
                    .turf(turf)
                    .imageUrl(uploaded.get("url"))
                    .cloudinaryPublicId(uploaded.get("publicId"))
                    .isPrimary(turf.getImages().isEmpty())  // First image = primary
                    .build();

                turf.getImages().add(image);

            } catch (Exception e) {
                // Rollback: delete from Cloudinary if it was uploaded but DB fails
                if (uploaded != null) {
                    log.warn("Rolling back Cloudinary upload for publicId={}",
                        uploaded.get("publicId"));
                    cloudinaryService.deleteImage(uploaded.get("publicId"));
                }
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to upload image: " + file.getOriginalFilename());
            }
        }

        log.info("Uploaded {} images for turfId={}", files.size(), turfId);
        return toDetailDto(turfRepository.save(turf));
    }

    /** Delete a specific image from a turf */
    @Transactional
    @PreAuthorize("hasRole('OWNER')")
    public void deleteImage(Long turfId, Long imageId, Long ownerId) {
        Turf turf = findTurfOrThrow(turfId);
        assertOwnership(turf, ownerId);

        TurfImage image = turf.getImages().stream()
            .filter(img -> img.getId().equals(imageId))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Image not found"));

        cloudinaryService.deleteImage(image.getCloudinaryPublicId());
        turf.getImages().remove(image);

        // Re-assign primary if deleted image was primary
        if (image.isPrimary() && !turf.getImages().isEmpty()) {
            turf.getImages().get(0).setPrimary(true);
        }

        turfRepository.save(turf);
        log.info("Deleted image id={} from turfId={}", imageId, turfId);
    }

    /** Soft-delete: owner deactivates their turf */
    @Transactional
    @PreAuthorize("hasRole('OWNER')")
    public void deactivateTurf(Long turfId, Long ownerId) {
        Turf turf = findTurfOrThrow(turfId);
        assertOwnership(turf, ownerId);
        turf.setStatus(TurfStatus.INACTIVE);
        turfRepository.save(turf);
        log.info("Turf deactivated: id={} by ownerId={}", turfId, ownerId);
    }

    /** Owner views their own turfs (all statuses) */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('OWNER')")
    public List<TurfSummaryDto> getMyTurfs(Long ownerId) {
        return turfRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId)
            .stream()
            .map(this::toSummaryDto)
            .collect(Collectors.toList());
    }

    // ─── ADMIN ENDPOINTS ─────────────────────────────────────────────────

    /** Admin approves a pending turf */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public TurfDetailDto approveTurf(Long turfId) {
        Turf turf = findTurfOrThrow(turfId);

        if (turf.getStatus() != TurfStatus.PENDING_APPROVAL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Turf is not in PENDING_APPROVAL status");
        }

        turf.setStatus(TurfStatus.ACTIVE);
        Turf saved = turfRepository.save(turf);
        log.info("Admin approved turf: id={}", turfId);

        // Generate 30 days of slots immediately — turf is live right now
        slotGenerationService.generateSlotsForNewlyApprovedTurf(saved.getId());

        return toDetailDto(saved);
    }

    /** Admin rejects a pending turf */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public TurfDetailDto rejectTurf(Long turfId, String reason) {
        Turf turf = findTurfOrThrow(turfId);
        turf.setStatus(TurfStatus.REJECTED);
        turf.setDescription(turf.getDescription() + "\n[Rejection reason: " + reason + "]");
        log.info("Admin rejected turf: id={}, reason={}", turfId, reason);
        return toDetailDto(turfRepository.save(turf));
    }

    /** Admin views all pending turfs */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<TurfSummaryDto> getPendingTurfs() {
        return turfRepository.findByStatusOrderByCreatedAtAsc(TurfStatus.PENDING_APPROVAL)
            .stream()
            .map(this::toSummaryDto)
            .collect(Collectors.toList());
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────

    /**
     * Loads a Turf with ALL collections initialized — owner, sportTypes, images.
     *
     * WHY TWO QUERIES?
     * Hibernate throws MultipleBagFetchException if you JOIN FETCH two List
     * collections (bags) in a single query. The solution is two separate queries:
     * - Query 1: turf + owner + sportTypes
     * - Query 2: turf + images (Hibernate merges into the same entity instance)
     * Both run within the same transaction so no LazyInitializationException.
     */
    private Turf findTurfOrThrow(Long turfId) {
        // Query 1 — loads owner + sportTypes
        Turf turf = turfRepository.findByIdWithSportTypesAndOwner(turfId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Turf not found with id: " + turfId));

        // Query 2 — loads images into the same entity instance
        turfRepository.findByIdWithImages(turfId);

        return turf;
    }

    /**
     * PBAC check — throws 403 if the requesting owner doesn't own this turf.
     * This is the second line of defense after @PreAuthorize("hasRole('OWNER')").
     */
    private void assertOwnership(Turf turf, Long requestingOwnerId) {
        if (!turf.getOwner().getId().equals(requestingOwnerId)) {
            log.warn("Ownership violation: ownerId={} tried to modify turfId={} owned by {}",
                requestingOwnerId, turf.getId(), turf.getOwner().getId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "You do not have permission to modify this turf");
        }
    }

    // ─── DTO MAPPERS ─────────────────────────────────────────────────────

    private TurfSummaryDto toSummaryDto(Turf turf) {
        // Get primary image URL safely — no lazy load needed (images loaded separately)
        String primaryImage = null;
        try {
            primaryImage = turf.getImages().stream()
                    .filter(TurfImage::isPrimary)
                    .map(TurfImage::getImageUrl)
                    .findFirst()
                    .orElse(turf.getImages().isEmpty() ? null
                            : turf.getImages().get(0).getImageUrl());
        } catch (Exception e) {
            // images not loaded in this context — safe to ignore for listing
            log.debug("Images not loaded for turfId={} in summary", turf.getId());
        }

        return TurfSummaryDto.builder()
                .id(turf.getId())
                .name(turf.getName())
                .city(turf.getCity())
                .state(turf.getState())
                .pricePerHour(turf.getPricePerHour())
                .averageRating(turf.getAverageRating())
                .totalReviews(turf.getTotalReviews())
                .status(turf.getStatus())
                .primaryImageUrl(primaryImage)
                .ownerName(turf.getOwner() != null ? turf.getOwner().getName() : null)
                .build();
    }

    private TurfDetailDto toDetailDto(Turf turf) {
        List<TurfDetailDto.ImageDto> imageDtos = turf.getImages().stream()
            .map(img -> TurfDetailDto.ImageDto.builder()
                .id(img.getId())
                .imageUrl(img.getImageUrl())
                .cloudinaryPublicId(img.getCloudinaryPublicId())
                .isPrimary(img.isPrimary())
                .build())
            .collect(Collectors.toList());

        return TurfDetailDto.builder()
            .id(turf.getId())
            .name(turf.getName())
            .description(turf.getDescription())
            .address(turf.getAddress())
            .city(turf.getCity())
            .state(turf.getState())
            .pincode(turf.getPincode())
            .latitude(turf.getLatitude())
            .longitude(turf.getLongitude())
            .pricePerHour(turf.getPricePerHour())
            .phone(turf.getPhone())
            .status(turf.getStatus())
            .averageRating(turf.getAverageRating())
            .totalReviews(turf.getTotalReviews())
            .sportTypes(turf.getSportTypes())
            .images(imageDtos)
            .ownerId(turf.getOwner().getId())
            .ownerName(turf.getOwner().getName())
            .createdAt(turf.getCreatedAt())
            .build();
    }

    /**
     * Dev/test helper — manually trigger slot generation for a specific turf.
     * Also useful after opening hours are updated.
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public String triggerSlotGeneration(Long turfId) {
        Turf turf = findTurfOrThrow(turfId);
        if (turf.getStatus() != TurfStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Turf must be ACTIVE to generate slots");
        }
        slotGenerationService.generateSlotsForNewlyApprovedTurf(turfId);
        return "Slot generation triggered for: " + turf.getName();
    }
}