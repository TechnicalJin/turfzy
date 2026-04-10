package com.turfzy.common;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Abstract base class inherited by ALL entities.
 * Provides: auto-managed createdAt + updatedAt via Spring Data JPA Auditing.
 *
 * @MappedSuperclass — not an entity itself, but maps fields to child entity tables.
 * @EntityListeners(AuditingEntityListener.class) — Spring auto-sets timestamps.
 *
 * To activate auditing, we need @EnableJpaAuditing in AppConfig (added below).
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}