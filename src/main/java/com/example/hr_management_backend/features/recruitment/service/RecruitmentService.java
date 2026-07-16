package com.example.hr_management_backend.features.recruitment.service;

import com.example.hr_management_backend.features.recruitment.model.JobPosting;
import com.example.hr_management_backend.features.recruitment.repository.JobPostingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RecruitmentService {

    private final JobPostingRepository jobPostingRepository;

    @Autowired
    public RecruitmentService(JobPostingRepository jobPostingRepository) {
        this.jobPostingRepository = jobPostingRepository;
    }

    public JobPosting createJobPosting(JobPosting jobPosting) {
        jobPosting.setId(null);
        jobPosting.setStatus("OPEN");
        return jobPostingRepository.save(jobPosting);
    }

    public List<JobPosting> getAllJobs() {
        return jobPostingRepository.findAll();
    }
}
