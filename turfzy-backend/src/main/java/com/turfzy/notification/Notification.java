package com.turfzy.notification;

import com.turfzy.common.BaseEntity;
import com.turfzy.user.User;
import jakarta.persistence.*;
import lombok.*;

/**
 * In-app notification record.
 * Also triggers email sending (Day 9) — the entity is the source of truth
 * for what was communicated to the user.
 */
@Entity
@Table(
    name = "notifications",
    indexes = {
        @Index(name = "idx_notification_user", columnList = "user_id"),
        @Index(name = "idx_notification_read", columnList = "is_read")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "message", columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean isRead = false;

    /** Optional — links notification to a specific booking */
    @Column(name = "reference_id")
    private Long referenceId;
}