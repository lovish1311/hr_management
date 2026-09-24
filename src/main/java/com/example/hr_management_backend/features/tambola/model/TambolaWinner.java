package com.example.hr_management_backend.features.tambola.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tambola_winners", uniqueConstraints = {
    @UniqueConstraint(name = "uk_tambola_winner_slot", columnNames = {"game_id", "prize_type", "prize_rank"})
}, indexes = {
    @Index(name = "idx_tambola_winner_game", columnList = "game_id"),
    @Index(name = "idx_tambola_winner_emp", columnList = "employee_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TambolaWinner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "employee_name", nullable = false)
    private String employeeName;

    @Column(name = "prize_type", nullable = false, length = 40)
    private String prizeType; // FIRST_HOUSE, SECOND_HOUSE, THIRD_HOUSE, FULL_HOUSE

    @Column(name = "prize_rank", nullable = false)
    @Builder.Default
    private int prizeRank = 1;

    @Column(name = "claim_number")
    private Integer claimNumber;

    @Column(name = "winning_details")
    private String winningDetails; // e.g. "Row 2 completed at Draw #18"

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @PrePersist
    protected void onCreate() {
        if (claimedAt == null) {
            claimedAt = LocalDateTime.now();
        }
    }
}
