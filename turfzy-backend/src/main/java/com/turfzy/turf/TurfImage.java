package com.turfzy.turf;

import com.turfzy.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Stores Cloudinary image URLs for a turf.
 * Kept as a separate entity (not @ElementCollection) because
 * we need the Cloudinary `publicId` for deletion — an @ElementCollection
 * can only store a single value per row.
 */
@Entity
@Table(name = "turf_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TurfImage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turf_id", nullable = false)
    private Turf turf;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;       // Full Cloudinary URL

    @Column(name = "cloudinary_public_id", nullable = false)
    private String cloudinaryPublicId;  // Needed to delete from Cloudinary

    @Column(name = "is_primary")
    @Builder.Default
    private boolean isPrimary = false;  // First image shown in listing card
}