package com.turfzy.turf.dto;

import com.turfzy.turf.SportType;
import com.turfzy.turf.TurfStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Full DTO for single turf detail page — includes all images, address, owner info.
 */
@Data
@Builder
public class TurfDetailDto {
    private Long id;
    private String name;
    private String description;
    private String address;
    private String city;
    private String state;
    private String pincode;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal pricePerHour;
    private String phone;
    private TurfStatus status;
    private BigDecimal averageRating;
    private Integer totalReviews;
    private List<SportType> sportTypes;
    private List<ImageDto> images;
    private Long ownerId;
    private String ownerName;
    private LocalDateTime createdAt;

    @Data
    @Builder
    public static class ImageDto {
        private Long id;
        private String imageUrl;
        private String cloudinaryPublicId;
        private boolean isPrimary;
    }
}