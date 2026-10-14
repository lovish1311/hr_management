package com.example.hr_management_backend.features.querybuilder.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "query_templates", indexes = {
    @Index(name = "idx_query_template_public", columnList = "is_public"),
    @Index(name = "idx_query_template_creator", columnList = "created_by_employee_id"),
    @Index(name = "idx_query_template_category", columnList = "category")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueryTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(length = 100)
    private String category; // e.g. "Workforce Demographics", "Payroll Audits", "Attendance Reports"

    @Builder.Default
    @Column(name = "is_public", nullable = false)
    private Boolean isPublic = true;

    @Column(name = "created_by_employee_id")
    private Long createdByEmployeeId;

    @Column(name = "query_config_json", nullable = false, columnDefinition = "TEXT")
    private String queryConfigJson;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
