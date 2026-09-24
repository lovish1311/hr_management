package com.example.hr_management_backend.features.tambola.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tambola_players", uniqueConstraints = {
    @UniqueConstraint(name = "uk_tambola_game_player", columnNames = {"game_id", "employee_id"})
}, indexes = {
    @Index(name = "idx_tambola_player_game", columnList = "game_id"),
    @Index(name = "idx_tambola_player_emp", columnList = "employee_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TambolaPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "employee_name", nullable = false)
    private String employeeName;

    @Column(name = "employee_email")
    private String employeeEmail;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @PrePersist
    protected void onCreate() {
        if (joinedAt == null) {
            joinedAt = LocalDateTime.now();
        }
    }
}
