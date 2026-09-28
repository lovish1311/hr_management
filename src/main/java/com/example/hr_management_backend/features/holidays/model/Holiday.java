package com.example.hr_management_backend.features.holidays.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "holidays", uniqueConstraints = {
    @UniqueConstraint(name = "uk_holiday_list_date", columnNames = {"holiday_list_id", "\"date\""})
}, indexes = {
    @Index(name = "idx_holiday_date", columnList = "\"date\""),
    @Index(name = "idx_holiday_list_id", columnList = "holiday_list_id"),
    @Index(name = "idx_holiday_type", columnList = "\"type\"")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Holiday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "holiday_list_id", nullable = false)
    private Long holidayListId;

    @Column(nullable = false)
    private String name;

    @Column(name = "\"date\"", nullable = false)
    private LocalDate date;

    @Column(name = "\"type\"", nullable = false)
    private String type; // GENERAL, RESTRICTED

    private String description;

    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (active == null) {
            active = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
