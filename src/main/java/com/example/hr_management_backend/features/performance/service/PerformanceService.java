package com.example.hr_management_backend.features.performance.service;

import com.example.hr_management_backend.features.performance.model.PerformanceReview;
import com.example.hr_management_backend.features.performance.repository.PerformanceReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PerformanceService {

    private final PerformanceReviewRepository performanceReviewRepository;

    @Autowired
    public PerformanceService(PerformanceReviewRepository performanceReviewRepository) {
        this.performanceReviewRepository = performanceReviewRepository;
    }

    public PerformanceReview submitReview(PerformanceReview review) {
        review.setId(null);
        return performanceReviewRepository.save(review);
    }

    public List<PerformanceReview> getReviewsForEmployee(Long employeeId) {
        return performanceReviewRepository.findByEmployeeId(employeeId);
    }
}
