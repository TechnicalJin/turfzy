// src/main/java/com/turfzy/turf/dto/TurfSummaryDto.java
package com.turfzy.turf.dto;

import com.turfzy.turf.SportType;
import com.turfzy.turf.TurfStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lightweight DTO for listing pages — only fields needed for turf cards.
 * Avoids loading full entity graph (images, owner) for every item in a list.
 */
@Data
@Builder
public class TurfSummaryDto {
    private Long id;
    private String name;
    private String city;
    private String state;
    private BigDecimal pricePerHour;
    private BigDecimal averageRating;
    private Integer totalReviews;
    private TurfStatus status;
    private List<SportType> sportTypes;
    private String primaryImageUrl;   // First image only for card thumbnail
    private String ownerName;
}