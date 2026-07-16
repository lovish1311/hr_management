package com.example.hr_management_backend.features.recruitment.repository;

import com.example.hr_management_backend.features.recruitment.model.JobPosting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {
}
