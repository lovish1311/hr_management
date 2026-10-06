package com.example.hr_management_backend.features.scribbil.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "draw_guess_scores", indexes = {
        @Index(name = "idx_draw_guess_score_emp", columnList = "employee_id"),
        @Index(name = "idx_draw_guess_score_room", columnList = "room_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DrawGuessScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_code", nullable = false, length = 10)
    private String roomCode;

    @Column(name = "round_id")
    private Long roundId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "employee_name", length = 100)
    private String employeeName;

    @Column(name = "points_awarded", nullable = false)
    private Integer pointsAwarded;

    @Column(name = "is_drawer_points", nullable = false)
    @Builder.Default
    private Boolean isDrawerPoints = false;

    @Column(name = "time_taken_seconds")
    private Double timeTakenSeconds;

    @CreationTimestamp
    @Column(name = "awarded_at", updatable = false)
    private LocalDateTime awardedAt;
}
