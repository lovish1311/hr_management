package com.example.hr_management_backend.features.auth.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_activation_tokens", indexes = {
        @Index(name = "idx_activation_key_hash", columnList = "activation_key_hash, used")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserActivationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "activation_key_hash", nullable = false, length = 64)
    private String activationKeyHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Builder.Default
    @Column(nullable = false)
    private Boolean used = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (used == null) used = false;
    }
}
