package com.example.hr_management_backend.features.employees.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Enterprise RBAC/ABAC Authority entity for employees.
 * Enables zero-downtime granular permission management (e.g. PAYROLL_MANAGE, LEAVE_APPROVE_ALL)
 * without requiring database schema alterations.
 */
@Entity
@Table(name = "employee_authorities",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_emp_authority", columnNames = {"employee_id", "authority"})
    },
    indexes = {
        @Index(name = "idx_emp_auth_employee_id", columnList = "employee_id"),
        @Index(name = "idx_emp_auth_authority", columnList = "authority")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeAuthority {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(nullable = false, length = 64)
    private String authority; // e.g. "PAYROLL_MANAGE", "LEAVE_APPROVE_ALL"

    @Column(name = "granted_by", length = 128)
    private String grantedBy; // Email of the Super Admin who assigned this authority

    @Column(name = "granted_at")
    private LocalDateTime grantedAt;

    @PrePersist
    protected void onCreate() {
        if (grantedAt == null) {
            grantedAt = LocalDateTime.now();
        }
        if (authority != null) {
            authority = authority.trim().toUpperCase();
        }
    }
}
