package com.turfzy.turf;

import com.turfzy.common.ApiResponse;
import com.turfzy.turf.dto.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Turf REST Controller.
 *
 * Route design:
 * GET  /api/turfs              → public search
 * GET  /api/turfs/{id}         → public detail
 * POST /api/owner/turfs        → owner creates turf
 * PUT  /api/owner/turfs/{id}   → owner updates turf
 * POST /api/owner/turfs/{id}/images  → owner uploads images
 * GET  /api/owner/turfs        → owner's turf list
 * POST /api/admin/turfs/{id}/approve → admin approves
 * POST /api/admin/turfs/{id}/reject  → admin rejects
 * GET  /api/admin/turfs/pending      → admin queue
 *
 * @AuthenticationPrincipal injects the UserDetails of the logged-in user.
 * We use userDetails.getUsername() (= email) to look up the User ID.
 */
@RestController
@CrossOrigin(origins = "http://localhost:5173")
public class TurfController {

    private static final Logger log = LoggerFactory.getLogger(TurfController.class);

    private final TurfService turfService;
    private final com.turfzy.user.UserRepository userRepository;

    public TurfController(TurfService turfService,
                          com.turfzy.user.UserRepository userRepository) {
        this.turfService = turfService;
        this.userRepository = userRepository;
    }

    // ─── PUBLIC ──────────────────────────────────────────────────────────

    @GetMapping("/api/turfs")
    public ResponseEntity<ApiResponse<Page<TurfSummaryDto>>> searchTurfs(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) SportType sport,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "12") int size) {

        Page<TurfSummaryDto> result = turfService.searchTurfs(city, sport, page, size);
        return ResponseEntity.ok(ApiResponse.success("Turfs fetched", result));
    }

    @GetMapping("/api/turfs/{id}")
    public ResponseEntity<ApiResponse<TurfDetailDto>> getTurf(@PathVariable Long id) {
        return ResponseEntity.ok(
            ApiResponse.success("Turf detail fetched", turfService.getTurfById(id)));
    }

    // ─── OWNER ───────────────────────────────────────────────────────────

    @PostMapping("/api/owner/turfs")
    public ResponseEntity<ApiResponse<TurfDetailDto>> createTurf(
            @Valid @RequestBody TurfCreateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long ownerId = resolveUserId(userDetails);
        TurfDetailDto created = turfService.createTurf(request, ownerId);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success("Turf created. Pending admin approval.", created));
    }

    @PutMapping("/api/owner/turfs/{id}")
    public ResponseEntity<ApiResponse<TurfDetailDto>> updateTurf(
            @PathVariable Long id,
            @Valid @RequestBody TurfUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long ownerId = resolveUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success("Turf updated",
            turfService.updateTurf(id, request, ownerId)));
    }

    @PostMapping(value = "/api/owner/turfs/{id}/images",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<TurfDetailDto>> uploadImages(
            @PathVariable Long id,
            @RequestPart("files") List<MultipartFile> files,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long ownerId = resolveUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success("Images uploaded",
            turfService.uploadImages(id, files, ownerId)));
    }

    @DeleteMapping("/api/owner/turfs/{turfId}/images/{imageId}")
    public ResponseEntity<ApiResponse<Void>> deleteImage(
            @PathVariable Long turfId,
            @PathVariable Long imageId,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long ownerId = resolveUserId(userDetails);
        turfService.deleteImage(turfId, imageId, ownerId);
        return ResponseEntity.ok(ApiResponse.success("Image deleted"));
    }

    @PatchMapping("/api/owner/turfs/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivateTurf(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        turfService.deactivateTurf(id, resolveUserId(userDetails));
        return ResponseEntity.ok(ApiResponse.success("Turf deactivated"));
    }

    @GetMapping("/api/owner/turfs")
    public ResponseEntity<ApiResponse<List<TurfSummaryDto>>> getMyTurfs(
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(ApiResponse.success("Owner turfs fetched",
            turfService.getMyTurfs(resolveUserId(userDetails))));
    }

    // ─── ADMIN ───────────────────────────────────────────────────────────

    @PostMapping("/api/admin/turfs/{id}/approve")
    public ResponseEntity<ApiResponse<TurfDetailDto>> approveTurf(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Turf approved",
            turfService.approveTurf(id)));
    }

    @PostMapping("/api/admin/turfs/{id}/reject")
    public ResponseEntity<ApiResponse<TurfDetailDto>> rejectTurf(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        String reason = body.getOrDefault("reason", "No reason provided");
        return ResponseEntity.ok(ApiResponse.success("Turf rejected",
            turfService.rejectTurf(id, reason)));
    }

    @GetMapping("/api/admin/turfs/pending")
    public ResponseEntity<ApiResponse<List<TurfSummaryDto>>> getPendingTurfs() {
        return ResponseEntity.ok(ApiResponse.success("Pending turfs fetched",
            turfService.getPendingTurfs()));
    }

    // ─── HELPER ──────────────────────────────────────────────────────────

    /**
     * Resolves logged-in user's DB ID from their email (JWT subject).
     * This pattern avoids passing userId in the request body — the server
     * always derives identity from the token, never trusts the client.
     */
    private Long resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "User not found"))
            .getId();
    }

    @PostMapping("/api/admin/turfs/{id}/generate-slots")
    public ResponseEntity<ApiResponse<String>> triggerSlotGen(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                "Done", turfService.triggerSlotGeneration(id)));
    }
}