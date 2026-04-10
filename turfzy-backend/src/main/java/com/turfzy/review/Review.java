package com.turfzy.review;

import com.turfzy.common.BaseEntity;
import com.turfzy.turf.Turf;
import com.turfzy.user.User;
import jakarta.persistence.*;
import lombok.*;

/**
 * Review — customers can review a turf after a CONFIRMED booking.
 *
 * UNIQUE constraint on (user_id, turf_id) — one review per turf per user.
 * Rating is 1–5, validated at service layer with @Min/@Max.
 */
@Entity
@Table(
    name = "reviews",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_turf_review",
                          columnNames = {"user_id", "turf_id"})
    },
    indexes = {
        @Index(name = "idx_review_turf", columnList = "turf_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turf_id", nullable = false)
    private Turf turf;

    @Column(name = "rating", nullable = false)
    private Integer rating;   // 1–5

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;
}