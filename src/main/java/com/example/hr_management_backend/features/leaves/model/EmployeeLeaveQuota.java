package com.example.hr_management_backend.features.leaves.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "employee_leave_quotas", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"employeeId", "\"year\"", "leaveType"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeLeaveQuota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long employeeId;

    @Column(name = "\"year\"", nullable = false)
    private Integer year;

    @Column(nullable = false)
    private String leaveType;

    @Builder.Default
    @Column(nullable = false)
    private Double quota = 0.0;

    @Builder.Default
    @Column(nullable = false)
    private Double used = 0.0;

    public double getRemaining() {
        return (quota != null ? quota : 0.0) - (used != null ? used : 0.0);
    }
}
