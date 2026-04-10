package com.turfzy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Base application configuration.
 * CORS is configured here for development — in production, Nginx handles CORS.
 *
 * NOTE: This CORS config will be overridden by SecurityFilterChain on Day 3.
 * We add it here so Day 1 health check works without Spring Security blocking it.
 */
@Configuration
@EnableJpaAuditing
public class AppConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                    .allowedOrigins(
                        "http://localhost:5173",  // Vite dev server
                        "http://localhost:3000"
                    )
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(true)
                    .maxAge(3600); // Cache preflight for 1 hour
            }
        };
    }
}