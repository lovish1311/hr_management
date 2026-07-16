package com.example.hr_management_backend.features.performance.controller;

import com.example.hr_management_backend.features.performance.model.PerformanceReview;
import com.example.hr_management_backend.features.performance.service.PerformanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/performance")
public class PerformanceController {

    private final PerformanceService performanceService;

    @Autowired
    public PerformanceController(PerformanceService performanceService) {
        this.performanceService = performanceService;
    }

    @PostMapping("/reviews")
    public ResponseEntity<PerformanceReview> submitReview(@RequestBody PerformanceReview review) {
        return ResponseEntity.ok(performanceService.submitReview(review));
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<PerformanceReview>> getReviewsForEmployee(@PathVariable Long employeeId) {
        return ResponseEntity.ok(performanceService.getReviewsForEmployee(employeeId));
    }
}
