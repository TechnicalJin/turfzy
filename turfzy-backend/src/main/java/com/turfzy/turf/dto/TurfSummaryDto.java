package com.turfzy.turf.dto;

import com.turfzy.turf.TurfStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Lightweight DTO for turf listing cards.
 *
 * Intentionally excludes sportTypes — fetching a collection for every
 * turf in a paginated list causes N+1 queries or LazyInitializationException.
 * SportTypes are included in TurfDetailDto (single turf view).
 *
 * Frontend turf cards only need: name, city, price, rating, image, status.
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
    private String primaryImageUrl;
    private String ownerName;
}