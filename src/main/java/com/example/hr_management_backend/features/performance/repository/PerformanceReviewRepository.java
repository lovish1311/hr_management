package com.example.hr_management_backend.features.performance.repository;

import com.example.hr_management_backend.features.performance.model.PerformanceReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PerformanceReviewRepository extends JpaRepository<PerformanceReview, Long> {
    List<PerformanceReview> findByEmployeeId(Long employeeId);
}
