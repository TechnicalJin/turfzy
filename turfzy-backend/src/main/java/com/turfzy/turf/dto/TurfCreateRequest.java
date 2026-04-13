// src/main/java/com/turfzy/turf/dto/TurfCreateRequest.java
package com.turfzy.turf.dto;

import com.turfzy.turf.SportType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class TurfCreateRequest {

    @NotBlank(message = "Turf name is required")
    @Size(min = 3, max = 150)
    private String name;

    @Size(max = 1000, message = "Description too long")
    private String description;

    @NotBlank(message = "Address is required")
    private String address;

    @NotBlank(message = "City is required")
    private String city;

    @NotBlank(message = "State is required")
    private String state;

    @Pattern(regexp = "^[1-9][0-9]{5}$", message = "Invalid Indian pincode")
    private String pincode;

    @NotNull(message = "Price per hour is required")
    @DecimalMin(value = "100.0", message = "Minimum price is ₹100")
    @DecimalMax(value = "10000.0", message = "Maximum price is ₹10,000")
    private BigDecimal pricePerHour;

    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid Indian mobile number")
    private String phone;

    @NotEmpty(message = "At least one sport type is required")
    private List<SportType> sportTypes;

    // Coordinates — optional for MVP, required for map feature
    private BigDecimal latitude;
    private BigDecimal longitude;
}