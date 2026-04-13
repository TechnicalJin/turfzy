package com.turfzy.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turfzy.auth.dto.AuthResponse;
import com.turfzy.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Called by Spring Security after successful Google OAuth2 login.
 *
 * Instead of redirecting to a success URL (default behavior),
 * we issue our own JWT and redirect to the frontend with the token
 * as a query parameter, which the React app stores in localStorage.
 *
 * Production alternative: use HttpOnly cookie for the token — but
 * for this MVP, query param → localStorage is simpler for SPA integration.
 */
@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2SuccessHandler.class);

    private final AuthService authService;

    public OAuth2SuccessHandler(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String email      = oAuth2User.getAttribute("email");
        String name       = oAuth2User.getAttribute("name");
        String googleId   = oAuth2User.getAttribute("sub");
        String pictureUrl = oAuth2User.getAttribute("picture");

        log.info("OAuth2 success for email: {}", email);

        AuthResponse authResponse = authService.processOAuth2Login(
            email, name, googleId, pictureUrl);

        // Redirect to frontend with token — React app picks this up on /oauth2/callback
        String redirectUrl = "http://localhost:5173/oauth2/callback?token="
            + authResponse.getAccessToken()
            + "&userId=" + authResponse.getUserId()
            + "&name=" + authResponse.getName()
            + "&role=" + authResponse.getRole();

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}