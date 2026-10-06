package com.example.hr_management_backend.features.scribbil.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "draw_guess_words", indexes = {
        @Index(name = "idx_draw_guess_word_category", columnList = "category"),
        @Index(name = "idx_draw_guess_word_difficulty", columnList = "difficulty")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DrawGuessWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String word;

    @Column(length = 50)
    @Builder.Default
    private String category = "GENERAL";

    @Column(length = 20)
    @Builder.Default
    private String difficulty = "MEDIUM"; // EASY, MEDIUM, HARD

    @Column(name = "hint_text", length = 200)
    private String hintText;

    @Column(name = "is_custom", nullable = false)
    @Builder.Default
    private Boolean isCustom = false;

    @Column(name = "created_by_employee_id")
    private Long createdByEmployeeId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
