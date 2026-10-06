package com.example.hr_management_backend.features.scribbil.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "draw_guess_rooms", indexes = {
        @Index(name = "idx_draw_guess_room_code", columnList = "room_code", unique = true),
        @Index(name = "idx_draw_guess_room_state", columnList = "state")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DrawGuessRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_code", nullable = false, length = 10, unique = true)
    private String roomCode;

    @Column(name = "room_name", length = 100)
    private String roomName;

    @Column(name = "host_employee_id", nullable = false)
    private Long hostEmployeeId;

    @Column(name = "host_name", length = 100)
    private String hostName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private DrawGuessGameState state = DrawGuessGameState.LOBBY;

    @Column(name = "max_rounds", nullable = false)
    @Builder.Default
    private Integer maxRounds = 3;

    @Column(name = "draw_time_seconds", nullable = false)
    @Builder.Default
    private Integer drawTimeSeconds = 80;

    @Column(name = "word_choice_count", nullable = false)
    @Builder.Default
    private Integer wordChoiceCount = 3;

    @Column(name = "category", length = 50)
    @Builder.Default
    private String category = "GENERAL";

    @Column(name = "custom_words_only", nullable = false)
    @Builder.Default
    private Boolean customWordsOnly = false;

    @Column(name = "current_round", nullable = false)
    @Builder.Default
    private Integer currentRound = 1;

    @Column(name = "current_turn_index", nullable = false)
    @Builder.Default
    private Integer currentTurnIndex = 0;

    @Column(name = "active_drawer_employee_id")
    private Long activeDrawerEmployeeId;

    @Column(name = "current_word", length = 100)
    private String currentWord;

    @Column(name = "current_hint", length = 100)
    private String currentHint;

    @Column(name = "turn_expires_at")
    private LocalDateTime turnExpiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
