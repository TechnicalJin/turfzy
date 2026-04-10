// src/main/java/com/turfzy/user/User.java
package com.turfzy.user;

import com.turfzy.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Core user entity — supports both email/password and Google OAuth2 login.
 *
 * Key design decisions:
 * - `password` is nullable — Google OAuth2 users don't have a password
 * - `googleId` stores the Google sub claim for OAuth2 users
 * - `isVerified` — email-verified users only (enforced on Day 3)
 * - `isActive` — soft-disable accounts without deleting data
 *
 * Roles: @ManyToMany with EAGER fetch — roles are small (3 values),
 * always needed for security checks, so eager is justified here.
 */
@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_user_email", columnList = "email"),
        @Index(name = "idx_user_google_id", columnList = "google_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "password")
    private String password;   // BCrypt hashed; null for OAuth2 users

    @Column(name = "phone", length = 15)
    private String phone;

    @Column(name = "profile_picture_url")
    private String profilePictureUrl;

    @Column(name = "google_id", unique = true)
    private String googleId;   // Google OAuth2 sub claim

    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private boolean isVerified = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    /** Helper: check if user has a specific role */
    public boolean hasRole(String roleName) {
        return roles.stream().anyMatch(r -> r.getName().equals(roleName));
    }
}