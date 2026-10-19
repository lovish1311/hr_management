package com.example.hr_management_backend.features.auth.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_emp_id", columnList = "employeeId"),
    @Index(name = "idx_user_email_status", columnList = "email, account_status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role;

    @Builder.Default
    @Column(name = "system_role", nullable = false, columnDefinition = "VARCHAR(255) DEFAULT 'NONE'")
    private String systemRole = "NONE"; // NONE, ADMIN, SUPER_ADMIN

    private Long employeeId;

    @Builder.Default
    @Column(name = "is_activated")
    private Boolean isActivated = false;

    @Builder.Default
    @Column(name = "account_status", length = 50)
    private String accountStatus = "PENDING_ACTIVATION"; // PENDING_ACTIVATION, ACTIVE, DEACTIVATED

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onInit() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (isActivated == null) {
            isActivated = false;
        }
        if (accountStatus == null || accountStatus.isBlank()) {
            accountStatus = "PENDING_ACTIVATION";
        }
    }
}
