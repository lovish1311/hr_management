package com.example.hr_management_backend.features.banners.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "banners", indexes = {
    @Index(name = "idx_banners_active_expiry_order", columnList = "is_active, expiry_date, display_order, created_at"),
    @Index(name = "idx_banners_type_expiry", columnList = "banner_type, expiry_date"),
    @Index(name = "idx_banners_target_emp", columnList = "target_employee_id, celebration_type, start_date, expiry_date"),
    @Index(name = "idx_banners_expiry", columnList = "expiry_date")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Banner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 255)
    private String subtitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BannerCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "banner_type", nullable = false, length = 32)
    @Builder.Default
    private BannerType bannerType = BannerType.NEWS;

    @Enumerated(EnumType.STRING)
    @Column(name = "celebration_type", length = 32)
    private CelebrationType celebrationType;

    @Column(name = "target_employee_id")
    private Long targetEmployeeId;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "action_url", length = 500)
    private String actionUrl;

    @Column(name = "action_button_text", length = 50)
    private String actionButtonText;

    @Column(name = "background_gradient", length = 100)
    private String backgroundGradient;

    @Column(name = "target_department", length = 100)
    private String targetDepartment; // null = global for all departments

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_by_id")
    private Long createdById;

    @Column(name = "created_by_email", length = 128)
    private String createdByEmail;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (isActive == null) {
            isActive = true;
        }
        if (displayOrder == null) {
            displayOrder = 0;
        }
        if (version == null) {
            version = 0L;
        }
        if (bannerType == null) {
            bannerType = BannerType.NEWS;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
