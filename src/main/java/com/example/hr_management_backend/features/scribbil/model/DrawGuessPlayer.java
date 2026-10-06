package com.example.hr_management_backend.features.scribbil.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "draw_guess_players", indexes = {
        @Index(name = "idx_draw_guess_player_room_emp", columnList = "room_code, employee_id", unique = true),
        @Index(name = "idx_draw_guess_player_room", columnList = "room_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DrawGuessPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_code", nullable = false, length = 10)
    private String roomCode;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "employee_name", nullable = false, length = 100)
    private String employeeName;

    @Column(name = "avatar_url", length = 255)
    private String avatarUrl;

    @Column(nullable = false)
    @Builder.Default
    private Integer score = 0;

    @Column(name = "turn_score", nullable = false)
    @Builder.Default
    private Integer turnScore = 0;

    @Column(name = "has_guessed_correctly", nullable = false)
    @Builder.Default
    private Boolean hasGuessedCorrectly = false;

    @Column(name = "is_drawer", nullable = false)
    @Builder.Default
    private Boolean isDrawer = false;

    @Column(name = "is_host", nullable = false)
    @Builder.Default
    private Boolean isHost = false;

    @Column(name = "is_connected", nullable = false)
    @Builder.Default
    private Boolean isConnected = true;

    @Column(name = "turn_order", nullable = false)
    @Builder.Default
    private Integer turnOrder = 0;

    @CreationTimestamp
    @Column(name = "joined_at", updatable = false)
    private LocalDateTime joinedAt;
}
