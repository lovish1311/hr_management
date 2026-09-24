package com.example.hr_management_backend.features.tambola.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tambola_draws", uniqueConstraints = {
    @UniqueConstraint(name = "uk_tambola_draw_game_number", columnNames = {"game_id", "number"}),
    @UniqueConstraint(name = "uk_tambola_draw_game_order", columnNames = {"game_id", "draw_order"})
}, indexes = {
    @Index(name = "idx_tambola_draw_game", columnList = "game_id"),
    @Index(name = "idx_tambola_draw_order", columnList = "game_id, draw_order")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TambolaDraw {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "draw_order", nullable = false)
    private int drawOrder; // 1, 2, 3...

    @Column(name = "number", nullable = false)
    private int number; // 1 to 90

    @Column(name = "drawn_at")
    private LocalDateTime drawnAt;

    @PrePersist
    protected void onCreate() {
        if (drawnAt == null) {
            drawnAt = LocalDateTime.now();
        }
    }
}
