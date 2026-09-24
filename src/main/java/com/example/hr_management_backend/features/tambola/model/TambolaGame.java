package com.example.hr_management_backend.features.tambola.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tambola_games", indexes = {
    @Index(name = "idx_tambola_room_code", columnList = "room_code", unique = true),
    @Index(name = "idx_tambola_game_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TambolaGame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_code", nullable = false, unique = true, length = 20)
    private String roomCode;

    @Column(length = 100)
    private String title;

    @Column(name = "auto_draw_interval_seconds")
    private Integer autoDrawIntervalSeconds;

    @Column(name = "manager_id", nullable = false)
    private Long managerId;

    @Column(name = "manager_name")
    private String managerName;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "WAITING"; // WAITING, RUNNING, PAUSED, COMPLETED, CANCELLED

    @Column(name = "total_numbers_drawn")
    @Builder.Default
    private int totalNumbersDrawn = 0;

    @Column(name = "last_drawn_number")
    private Integer lastDrawnNumber;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = "WAITING";
        }
    }
}
