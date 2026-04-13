package com.turfzy.auth;

import com.turfzy.auth.dto.AuthResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2SuccessHandler.class);

    private final AuthService authService;

    // @Lazy breaks the cycle — Spring injects a proxy at startup,
    // resolves the real AuthService bean only on first actual method call.
    public OAuth2SuccessHandler(@Lazy AuthService authService) {
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

        String redirectUrl = "http://localhost:5173/oauth2/callback?token="
                + authResponse.getAccessToken()
                + "&userId=" + authResponse.getUserId()
                + "&name=" + authResponse.getName()
                + "&role=" + authResponse.getRole();

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}