package com.turfzy.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Health check endpoint — used by AWS ALB health checks + uptime monitoring.
 * Must always return 200 OK when the app is healthy.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        log.info("Health check requested at {}", LocalDateTime.now());

        Map<String, Object> healthData = Map.of(
            "status", "UP",
            "service", "Turfzy Backend",
            "timestamp", LocalDateTime.now().toString(),
            "version", "1.0.0"
        );

        return ResponseEntity.ok(ApiResponse.success("Turfzy is running!", healthData));
    }
}