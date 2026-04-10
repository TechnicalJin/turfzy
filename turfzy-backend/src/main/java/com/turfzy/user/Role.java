package com.turfzy.user;

import com.turfzy.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * System roles: CUSTOMER, OWNER, ADMIN.
 * A User can have multiple roles (e.g., someone could be both CUSTOMER and OWNER).
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Role extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;   // e.g., "ROLE_CUSTOMER", "ROLE_OWNER", "ROLE_ADMIN"
}