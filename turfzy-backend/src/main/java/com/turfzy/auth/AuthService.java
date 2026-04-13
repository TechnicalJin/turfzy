package com.turfzy.auth;

import com.turfzy.auth.dto.AuthResponse;
import com.turfzy.auth.dto.LoginRequest;
import com.turfzy.auth.dto.RegisterRequest;
import com.turfzy.common.ApiResponse;
import com.turfzy.user.Role;
import com.turfzy.user.User;
import com.turfzy.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Map;
import java.util.Set;

/**
 * Core authentication business logic.
 * Controller only calls this — no DB access in the controller layer.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @PersistenceContext
    private EntityManager entityManager;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Registration attempt for email: {}", request.getEmail());

        // Check duplicate email
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Email already registered");
        }

        // Resolve role — use EntityManager.getReference for performance
        // (avoids SELECT; uses proxy since we only need the FK)
        String roleName = "OWNER".equalsIgnoreCase(request.getRole())
                ? "ROLE_OWNER" : "ROLE_CUSTOMER";

        Role role = findRoleByName(roleName);

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .isVerified(true)   // Simplified: skip email verification for MVP
                .isActive(true)
                .roles(Set.of(role))
                .build();

        User saved = userRepository.save(user);
        log.info("User registered successfully: id={}, email={}", saved.getId(), saved.getEmail());

        return buildAuthResponse(saved);
    }

    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        try {
            // Spring Security validates credentials + calls CustomUserDetailsService
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail().toLowerCase(),
                            request.getPassword()
                    )
            );
        } catch (BadCredentialsException e) {
            log.warn("Failed login attempt for email: {}", request.getEmail());
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        User user = userRepository.findByEmail(request.getEmail().toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!user.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is disabled");
        }

        log.info("Login successful for user: id={}", user.getId());
        return buildAuthResponse(user);
    }

    /** Called from OAuth2SuccessHandler after Google login */
    @Transactional
    public AuthResponse processOAuth2Login(String email, String name,
                                           String googleId, String pictureUrl) {
        log.info("OAuth2 login for email: {}", email);

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            // First-time Google login — auto-register as CUSTOMER
            log.info("First OAuth2 login — creating account for: {}", email);
            Role customerRole = findRoleByName("ROLE_CUSTOMER");

            return userRepository.save(User.builder()
                    .name(name)
                    .email(email.toLowerCase())
                    .googleId(googleId)
                    .profilePictureUrl(pictureUrl)
                    .isVerified(true)
                    .isActive(true)
                    .roles(Set.of(customerRole))
                    .build());
        });

        // Update Google ID if user registered by email first, then uses Google
        if (user.getGoogleId() == null) {
            user.setGoogleId(googleId);
            user.setProfilePictureUrl(pictureUrl);
            userRepository.save(user);
        }

        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        // Determine primary role for frontend routing
        String primaryRole = user.getRoles().stream()
                .map(Role::getName)
                .filter(r -> r.equals("ROLE_ADMIN"))
                .findFirst()
                .orElse(user.getRoles().stream()
                        .map(Role::getName)
                        .filter(r -> r.equals("ROLE_OWNER"))
                        .findFirst()
                        .orElse("ROLE_CUSTOMER"));

        Map<String, Object> claims = Map.of(
                "role", primaryRole,
                "userId", user.getId()
        );

        String accessToken = jwtService.generateAccessToken(user.getEmail(), claims);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(86400000L)
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(primaryRole)
                .profilePicture(user.getProfilePictureUrl())
                .build();
    }

    private Role findRoleByName(String roleName) {
        return entityManager
                .createQuery("SELECT r FROM Role r WHERE r.name = :name", Role.class)
                .setParameter("name", roleName)
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Role not found: " + roleName + ". Ensure DataSeeder ran on startup."));
    }
}