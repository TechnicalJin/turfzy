package com.turfzy.auth;

import com.turfzy.auth.dto.AuthResponse;
import com.turfzy.auth.dto.LoginRequest;
import com.turfzy.auth.dto.RegisterRequest;
import com.turfzy.user.Role;
import com.turfzy.user.RoleRepository;
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

import java.util.Map;
import java.util.Set;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Registration attempt for email: {}", request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        String roleName = "OWNER".equalsIgnoreCase(request.getRole())
                ? "ROLE_OWNER" : "ROLE_CUSTOMER";

        Role role = findRoleByName(roleName);

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .isVerified(true)
                .isActive(true)
                .roles(Set.of(role))
                .build();

        User saved = userRepository.save(user);
        log.info("User registered: id={}, email={}", saved.getId(), saved.getEmail());
        return buildAuthResponse(saved);
    }

    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail().toLowerCase(),
                            request.getPassword()
                    )
            );
        } catch (BadCredentialsException e) {
            log.warn("Failed login for: {}", request.getEmail());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        User user = userRepository.findByEmail(request.getEmail().toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!user.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is disabled");
        }

        log.info("Login successful: id={}", user.getId());
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse processOAuth2Login(String email, String name,
                                           String googleId, String pictureUrl) {
        log.info("OAuth2 login for email: {}", email);

        User user = userRepository.findByEmail(email).orElseGet(() -> {
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

        if (user.getGoogleId() == null) {
            user.setGoogleId(googleId);
            user.setProfilePictureUrl(pictureUrl);
            userRepository.save(user);
        }

        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String primaryRole = user.getRoles().stream()
                .map(Role::getName)
                .filter(r -> r.equals("ROLE_ADMIN")).findFirst()
                .orElse(user.getRoles().stream()
                        .map(Role::getName)
                        .filter(r -> r.equals("ROLE_OWNER")).findFirst()
                        .orElse("ROLE_CUSTOMER"));

        Map<String, Object> claims = Map.of("role", primaryRole, "userId", user.getId());
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
        return roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException(
                        "Role not found: " + roleName + ". Ensure DataSeeder ran on startup."));
    }
}