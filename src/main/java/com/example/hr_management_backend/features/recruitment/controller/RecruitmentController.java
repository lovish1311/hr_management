package com.example.hr_management_backend.features.recruitment.controller;

import com.example.hr_management_backend.features.recruitment.model.JobPosting;
import com.example.hr_management_backend.features.recruitment.service.RecruitmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recruitment")
public class RecruitmentController {

    private final RecruitmentService recruitmentService;

    @Autowired
    public RecruitmentController(RecruitmentService recruitmentService) {
        this.recruitmentService = recruitmentService;
    }

    @PostMapping("/jobs")
    public ResponseEntity<JobPosting> createJobPosting(@RequestBody JobPosting jobPosting) {
        return ResponseEntity.ok(recruitmentService.createJobPosting(jobPosting));
    }

    @GetMapping("/jobs")
    public ResponseEntity<List<JobPosting>> getAllJobs() {
        return ResponseEntity.ok(recruitmentService.getAllJobs());
    }
}
