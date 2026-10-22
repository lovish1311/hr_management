package com.example.hr_management_backend.features.banners.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "banner_reactions",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_banner_emp_reaction", columnNames = {"banner_id", "employee_id", "reaction_type"})
    },
    indexes = {
        @Index(name = "idx_banner_reactions_banner_type", columnList = "banner_id, reaction_type"),
        @Index(name = "idx_banner_reactions_emp_banner", columnList = "employee_id, banner_id")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BannerReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "banner_id", nullable = false)
    private Long bannerId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reaction_type", nullable = false, length = 32)
    private BannerReactionType reactionType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
