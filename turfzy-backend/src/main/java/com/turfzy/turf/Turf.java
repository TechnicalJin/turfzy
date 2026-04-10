package com.turfzy.turf;

import com.turfzy.common.BaseEntity;
import com.turfzy.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Turf entity — the core listing object.
 *
 * Key design decisions:
 * - `pricePerHour` uses BigDecimal (never use float/double for money)
 * - `owner` is LAZY — we don't always need owner details when listing turfs
 * - `images` are @OneToMany with CascadeType.ALL — deleting a turf
 *   cascades to its images (both in DB and Cloudinary, handled in service)
 * - `sportTypes` stored as ENUM STRING in a @ElementCollection table
 *   (a turf can support multiple sports)
 * - `averageRating` is a denormalized field — updated whenever a review
 *   is posted/deleted. Avoids expensive AVG() on every turf listing query.
 */
@Entity
@Table(
    name = "turfs",
    indexes = {
        @Index(name = "idx_turf_city", columnList = "city"),
        @Index(name = "idx_turf_owner", columnList = "owner_id"),
        @Index(name = "idx_turf_status", columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Turf extends BaseEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "address", nullable = false, length = 300)
    private String address;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "state", nullable = false, length = 100)
    private String state;

    @Column(name = "pincode", length = 10)
    private String pincode;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "price_per_hour", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerHour;

    @Column(name = "phone", length = 15)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private TurfStatus status = TurfStatus.PENDING_APPROVAL;

    @Column(name = "average_rating", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal averageRating = BigDecimal.ZERO;

    @Column(name = "total_reviews")
    @Builder.Default
    private Integer totalReviews = 0;

    // Many turfs belong to one owner
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    // One turf supports multiple sport types
    @ElementCollection(targetClass = SportType.class)
    @CollectionTable(name = "turf_sport_types",
                     joinColumns = @JoinColumn(name = "turf_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "sport_type")
    @Builder.Default
    private List<SportType> sportTypes = new ArrayList<>();

    // One turf has many images (Cloudinary URLs)
    @OneToMany(mappedBy = "turf", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TurfImage> images = new ArrayList<>();
}