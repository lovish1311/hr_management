package com.example.hr_management_backend.features.tambola.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tambola_tickets", uniqueConstraints = {
    @UniqueConstraint(name = "uk_tambola_ticket_game_employee", columnNames = {"game_id", "employee_id"})
}, indexes = {
    @Index(name = "idx_tambola_ticket_game", columnList = "game_id"),
    @Index(name = "idx_tambola_ticket_emp", columnList = "employee_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TambolaTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "numbers_json", columnDefinition = "TEXT", nullable = false)
    private String numbersJson; // 3x9 2D JSON array [[...], [...], [...]]

    @Column(name = "row1", nullable = false)
    private String row1; // e.g. "4,12,35,62,81"

    @Column(name = "row2", nullable = false)
    private String row2;

    @Column(name = "row3", nullable = false)
    private String row3;

    @Column(name = "all_numbers", nullable = false, length = 100)
    private String allNumbers; // comma-separated 15 numbers

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
