// src/main/java/com/turfzy/turf/dto/TurfUpdateRequest.java
package com.turfzy.turf.dto;

import com.turfzy.turf.SportType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/**
 * Partial update — all fields optional (PATCH semantics).
 * Service only updates non-null fields.
 */
@Data
public class TurfUpdateRequest {

    @Size(min = 3, max = 150)
    private String name;

    @Size(max = 1000)
    private String description;

    private String address;
    private String city;
    private String state;

    @Pattern(regexp = "^[1-9][0-9]{5}$")
    private String pincode;

    @DecimalMin("100.0") @DecimalMax("10000.0")
    private BigDecimal pricePerHour;

    @Pattern(regexp = "^[6-9]\\d{9}$")
    private String phone;

    private List<SportType> sportTypes;
    private BigDecimal latitude;
    private BigDecimal longitude;

    private LocalTime openingTime;
    private LocalTime closingTime;
}