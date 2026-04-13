package com.turfzy.config;

import com.turfzy.user.Role;
import com.turfzy.user.RoleRepository;
import com.turfzy.user.User;
import com.turfzy.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Seeds roles and default admin on every startup.
 * Uses RoleRepository (not raw EntityManager) for reliability.
 * @Transactional ensures all inserts commit before the app serves requests.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(RoleRepository roleRepository,
                      UserRepository userRepository,
                      PasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedRoles();
        seedAdminUser();
        log.info("DataSeeder completed successfully.");
    }

    private void seedRoles() {
        for (String roleName : new String[]{"ROLE_CUSTOMER", "ROLE_OWNER", "ROLE_ADMIN"}) {
            if (!roleRepository.existsByName(roleName)) {
                Role role = new Role();
                role.setName(roleName);
                roleRepository.save(role);
                log.info("Seeded role: {}", roleName);
            } else {
                log.info("Role already exists, skipping: {}", roleName);
            }
        }
    }

    private void seedAdminUser() {
        String adminEmail = "admin@turfzy.com";
        if (userRepository.existsByEmail(adminEmail)) {
            log.info("Admin user already exists, skipping.");
            return;
        }

        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseThrow(() -> new IllegalStateException("ROLE_ADMIN not found after seeding"));

        User admin = User.builder()
                .name("Turfzy Admin")
                .email(adminEmail)
                .password(passwordEncoder.encode("Admin@1234"))
                .isVerified(true)
                .isActive(true)
                .roles(Set.of(adminRole))
                .build();

        userRepository.save(admin);
        log.info("Seeded admin user: {}", adminEmail);
    }
}