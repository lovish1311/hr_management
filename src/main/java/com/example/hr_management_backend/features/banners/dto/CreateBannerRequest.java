package com.example.hr_management_backend.features.banners.dto;

import com.example.hr_management_backend.features.banners.model.BannerCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateBannerRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 150, message = "Title cannot exceed 150 characters")
    private String title;

    @Size(max = 255, message = "Subtitle cannot exceed 255 characters")
    private String subtitle;

    @NotNull(message = "Category is required")
    private BannerCategory category;

    @Size(max = 500, message = "Image URL cannot exceed 500 characters")
    private String imageUrl;

    @Size(max = 500, message = "Action URL cannot exceed 500 characters")
    private String actionUrl;

    @Size(max = 50, message = "Action button text cannot exceed 50 characters")
    private String actionButtonText;

    @Size(max = 100, message = "Background gradient cannot exceed 100 characters")
    private String backgroundGradient;

    @Size(max = 100, message = "Target department cannot exceed 100 characters")
    private String targetDepartment; // null or blank = all departments

    @NotNull(message = "Start date is required")
    private LocalDateTime startDate;

    @NotNull(message = "Expiry date is required")
    private LocalDateTime expiryDate;

    @Builder.Default
    private Integer displayOrder = 0;

    @Builder.Default
    private Boolean isActive = true;
}
