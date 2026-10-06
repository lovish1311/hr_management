package com.example.hr_management_backend.features.payroll.arrears;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArrearsRecordRepository extends JpaRepository<ArrearsRecord, Long> {
    List<ArrearsRecord> findByTargetMonthIgnoreCaseAndTargetYearAndIsProcessedFalse(String targetMonth, Integer targetYear);
    List<ArrearsRecord> findByEmployeeIdAndTargetMonthIgnoreCaseAndTargetYear(Long employeeId, String targetMonth, Integer targetYear);
    List<ArrearsRecord> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);
}
