package com.example.hr_management_backend.features.banners.dto;

import com.example.hr_management_backend.features.banners.model.BannerCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BannerDto {

    private Long id;
    private String title;
    private String subtitle;
    private BannerCategory category;
    private String imageUrl;
    private String actionUrl;
    private String actionButtonText;
    private String backgroundGradient;
    private String targetDepartment;
    private LocalDateTime startDate;
    private LocalDateTime expiryDate;
    private Integer displayOrder;
    private Boolean isActive;
    private Long createdById;
    private String createdByEmail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    private boolean isCurrentlyActive;
    private boolean isExpired;
    private boolean isScheduled;
}
