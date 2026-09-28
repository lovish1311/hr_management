package com.example.hr_management_backend.features.holidays.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "holiday_lists", uniqueConstraints = {
    @UniqueConstraint(name = "uk_holiday_list_year_group", columnNames = {"\"year\"", "applicable_group"})
}, indexes = {
    @Index(name = "idx_holiday_list_year", columnList = "\"year\"")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HolidayList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "\"year\"", nullable = false)
    private Integer year;

    private String description;

    @Builder.Default
    @Column(name = "applicable_group")
    private String applicableGroup = "ALL"; // For future extensibility: ALL, location, department

    @Builder.Default
    private Boolean active = true;

    @Builder.Default
    @Column(name = "published")
    private Boolean published = false;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (applicableGroup == null || applicableGroup.isBlank()) {
            applicableGroup = "ALL";
        }
        if (published == null) {
            published = false;
        }
        if (active == null) {
            active = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
