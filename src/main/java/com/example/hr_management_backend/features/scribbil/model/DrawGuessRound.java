package com.example.hr_management_backend.features.scribbil.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "draw_guess_rounds", indexes = {
        @Index(name = "idx_draw_guess_round_room", columnList = "room_code, round_number")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DrawGuessRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_code", nullable = false, length = 10)
    private String roomCode;

    @Column(name = "round_number", nullable = false)
    private Integer roundNumber;

    @Column(name = "turn_number", nullable = false)
    private Integer turnNumber;

    @Column(name = "drawer_employee_id", nullable = false)
    private Long drawerEmployeeId;

    @Column(name = "drawer_name", length = 100)
    private String drawerName;

    @Column(name = "word", nullable = false, length = 100)
    private String word;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "correct_guesses_count")
    @Builder.Default
    private Integer correctGuessesCount = 0;

    @CreationTimestamp
    @Column(name = "started_at", updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;
}
