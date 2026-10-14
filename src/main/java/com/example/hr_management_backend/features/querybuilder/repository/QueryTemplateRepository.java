package com.example.hr_management_backend.features.querybuilder.repository;

import com.example.hr_management_backend.features.querybuilder.model.QueryTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QueryTemplateRepository extends JpaRepository<QueryTemplate, Long> {

    @Query("SELECT q FROM QueryTemplate q WHERE q.isPublic = true OR q.createdByEmployeeId = :employeeId ORDER BY q.createdAt DESC")
    List<QueryTemplate> findAccessibleTemplates(@Param("employeeId") Long employeeId);

    List<QueryTemplate> findByCategory(String category);
}
