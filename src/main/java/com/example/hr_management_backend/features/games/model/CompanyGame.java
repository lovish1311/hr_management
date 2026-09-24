package com.example.hr_management_backend.features.games.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "company_games")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyGame {

    @Id
    @Column(name = "game_key", length = 64, nullable = false, unique = true)
    private String gameKey;

    @Column(name = "title", length = 128, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", length = 64)
    private String category;

    @Column(name = "icon_name", length = 64)
    private String iconName;

    @Column(name = "gradient_start", length = 16)
    private String gradientStart;

    @Column(name = "gradient_end", length = 16)
    private String gradientEnd;

    @Builder.Default
    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = true;

    @Builder.Default
    @Column(name = "allowed_roles", length = 255)
    private String allowedRoles = "ROLE_EMPLOYEE,ROLE_HR_ADMIN,ROLE_SUPER_ADMIN";

    @Builder.Default
    @Column(name = "min_players")
    private Integer minPlayers = 2;

    @Builder.Default
    @Column(name = "max_players")
    private Integer maxPlayers = 100;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
