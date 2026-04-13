package com.turfzy.config;

import com.turfzy.user.Role;
import com.turfzy.user.User;
import com.turfzy.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Seeds essential reference data on every startup (idempotent — safe to run repeatedly).
 *
 * Seeds:
 * 1. ROLE_CUSTOMER, ROLE_OWNER, ROLE_ADMIN
 * 2. Default admin user (dev only — remove in prod or use env variables)
 *
 * CommandLineRunner runs after the ApplicationContext is fully loaded.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    public DataSeeder(UserRepository userRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedRoles();
        seedAdminUser();
    }

    private void seedRoles() {
        List<String> roleNames = List.of("ROLE_CUSTOMER", "ROLE_OWNER", "ROLE_ADMIN");

        for (String roleName : roleNames) {
            long count = entityManager
                .createQuery("SELECT COUNT(r) FROM Role r WHERE r.name = :name", Long.class)
                .setParameter("name", roleName)
                .getSingleResult();

            if (count == 0) {
                Role role = new Role();
                role.setName(roleName);
                entityManager.persist(role);
                log.info("Seeded role: {}", roleName);
            }
        }

        entityManager.flush();
        log.info("Role seeding complete.");
    }

    private void seedAdminUser() {
        String adminEmail = "admin@turfzy.com";

        if (!userRepository.existsByEmail(adminEmail)) {
            Role adminRole = entityManager
                .createQuery("SELECT r FROM Role r WHERE r.name = 'ROLE_ADMIN'", Role.class)
                .getSingleResult();

            User admin = User.builder()
                .name("Turfzy Admin")
                .email(adminEmail)
                .password(passwordEncoder.encode("Admin@1234"))
                .isVerified(true)
                .isActive(true)
                .roles(Set.of(adminRole))
                .build();

            userRepository.save(admin);
            log.info("Seeded default admin user: {}", adminEmail);
        }
    }
}