package com.turfzy.common;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Wraps Cloudinary SDK for image upload and deletion.
 *
 * Why Cloudinary?
 * - Free tier: 25GB storage, 25GB bandwidth/month
 * - Auto CDN delivery globally
 * - URL-based transformations: append ?w=400&h=300&c=fill to any URL
 * - We store publicId for deletion; URL for display
 *
 * Upload folder structure: turfzy/turfs/{turfId}/{uuid}
 * This lets us bulk-delete all images for a turf via folder deletion.
 */
@Service
public class CloudinaryService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryService.class);
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final String[] ALLOWED_TYPES = {"image/jpeg", "image/png", "image/webp"};

    private final Cloudinary cloudinary;

    public CloudinaryService(
            @Value("${cloudinary.cloud-name}") String cloudName,
            @Value("${cloudinary.api-key}") String apiKey,
            @Value("${cloudinary.api-secret}") String apiSecret) {
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
            "cloud_name", cloudName,
            "api_key",    apiKey,
            "api_secret", apiSecret,
            "secure",     true   // Always use HTTPS URLs
        ));
        log.info("Cloudinary initialized for cloud: {}", cloudName);
    }

    /**
     * Uploads an image to Cloudinary under the turf's folder.
     * @return Map with "url" and "publicId"
     */
    public Map<String, String> uploadTurfImage(MultipartFile file, Long turfId) {
        validateImageFile(file);

        try {
            String publicId = "turfzy/turfs/" + turfId + "/" + UUID.randomUUID();

            Map<?, ?> result = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                    "public_id",       publicId,
                    "resource_type",   "image",
                    "transformation",  "q_auto,f_auto",  // Auto quality + format
                    "overwrite",       false
                )
            );

            String url = (String) result.get("secure_url");
            log.info("Uploaded image to Cloudinary: publicId={}, url={}", publicId, url);

            return Map.of("url", url, "publicId", publicId);

        } catch (IOException e) {
            log.error("Cloudinary upload failed for turfId={}: {}", turfId, e.getMessage());
            throw new RuntimeException("Image upload failed. Please try again.", e);
        }
    }

    /**
     * Deletes an image from Cloudinary by publicId.
     * Called when a turf image is removed or the turf is deleted.
     */
    public void deleteImage(String publicId) {
        try {
            Map<?, ?> result = cloudinary.uploader().destroy(
                publicId, ObjectUtils.emptyMap());
            log.info("Deleted Cloudinary image: publicId={}, result={}", publicId, result.get("result"));
        } catch (IOException e) {
            // Log but don't throw — a failed image delete shouldn't break the main flow
            log.error("Failed to delete Cloudinary image: publicId={}, error={}", publicId, e.getMessage());
        }
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image file cannot be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Image size must not exceed 5MB");
        }
        String contentType = file.getContentType();
        boolean validType = false;
        for (String allowed : ALLOWED_TYPES) {
            if (allowed.equals(contentType)) { validType = true; break; }
        }
        if (!validType) {
            throw new IllegalArgumentException(
                "Only JPEG, PNG, and WebP images are allowed");
        }
    }
}