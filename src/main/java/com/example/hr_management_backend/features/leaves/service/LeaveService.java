package com.example.hr_management_backend.features.leaves.service;

import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import com.example.hr_management_backend.features.leaves.model.LeaveBalance;
import com.example.hr_management_backend.features.leaves.model.LeaveRequest;
import com.example.hr_management_backend.features.leaves.repository.LeaveBalanceRepository;
import com.example.hr_management_backend.features.leaves.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeaveService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional
    public LeaveBalance getOrCreateLeaveBalance(Long employeeId, Integer year) {
        return leaveBalanceRepository.findByEmployeeIdAndYear(employeeId, year)
                .orElseGet(() -> {
                    LeaveBalance initial = LeaveBalance.builder()
                            .employeeId(employeeId)
                            .year(year)
                            .casualLeaveQuota(12)
                            .casualLeaveUsed(0)
                            .sickLeaveQuota(10)
                            .sickLeaveUsed(0)
                            .earnedLeaveQuota(15)
                            .earnedLeaveUsed(0)
                            .build();
                    return leaveBalanceRepository.save(initial);
                });
    }

    @Transactional
    public LeaveRequest applyForLeave(LeaveRequest request) {
        request.setId(null);
        request.setStatus("PENDING");

        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new IllegalArgumentException("Start date and end date are required");
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }

        // Dynamically resolve assigned manager for leave routing
        if (request.getEmployeeId() != null) {
            employeeRepository.findById(request.getEmployeeId()).ifPresent(emp -> {
                if (emp.getManager() != null) {
                    request.setApprovedBy(emp.getManager().getId());
                    log.info("Leave request routed dynamically to assigned manager: {} (ID: {})",
                            emp.getManager().getFirstName() + " " + emp.getManager().getLastName(), emp.getManager().getId());
                }
            });
        }

        long days = ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) + 1;
        request.setTotalDays((int) days);

        if (request.getLeaveType() == null || request.getLeaveType().isBlank()) {
            request.setLeaveType("CASUAL");
        }

        int currentYear = request.getStartDate().getYear();
        LeaveBalance balance = getOrCreateLeaveBalance(request.getEmployeeId(), currentYear);

        int remaining = switch (request.getLeaveType().toUpperCase()) {
            case "SICK" -> balance.getSickLeaveRemaining();
            case "EARNED" -> balance.getEarnedLeaveRemaining();
            case "UNPAID", "WORK_FROM_HOME" -> 999;
            default -> balance.getCasualLeaveRemaining();
        };

        if (!"UNPAID".equalsIgnoreCase(request.getLeaveType()) && !"WORK_FROM_HOME".equalsIgnoreCase(request.getLeaveType()) && days > remaining) {
            throw new IllegalStateException("Insufficient leave balance. Remaining: " + remaining + " days.");
        }

        return leaveRequestRepository.save(request);
    }


    /**
     * Approves or rejects a leave request.
     * Uses Pessimistic DB Locking on LeaveBalance to prevent race conditions during deduction.
     */
    @Transactional
    public LeaveRequest updateStatus(Long requestId, String status, String rejectionReason, Long approverId) {
        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Leave request not found with id: " + requestId));

        if (!"PENDING".equalsIgnoreCase(request.getStatus())) {
            throw new IllegalStateException("Leave request has already been " + request.getStatus());
        }

        request.setStatus(status.toUpperCase());
        request.setRejectionReason(rejectionReason);
        request.setApprovedBy(approverId);

        if ("APPROVED".equalsIgnoreCase(status)) {
            int year = request.getStartDate().getYear();
            
            // PESSIMISTIC LOCK: Lock leave balance row for update
            LeaveBalance balance = leaveBalanceRepository.findByEmployeeIdAndYearForUpdate(request.getEmployeeId(), year)
                    .orElseGet(() -> getOrCreateLeaveBalance(request.getEmployeeId(), year));

            int requestedDays = request.getTotalDays();

            switch (request.getLeaveType().toUpperCase()) {
                case "SICK" -> {
                    if (balance.getSickLeaveRemaining() < requestedDays) {
                        throw new IllegalStateException("Cannot approve: Insufficient sick leave balance.");
                    }
                    balance.setSickLeaveUsed(balance.getSickLeaveUsed() + requestedDays);
                }
                case "EARNED" -> {
                    if (balance.getEarnedLeaveRemaining() < requestedDays) {
                        throw new IllegalStateException("Cannot approve: Insufficient earned leave balance.");
                    }
                    balance.setEarnedLeaveUsed(balance.getEarnedLeaveUsed() + requestedDays);
                }
                case "WORK_FROM_HOME" -> {
                    balance.setWorkFromHomeUsed(balance.getWorkFromHomeUsed() + requestedDays);
                }
                case "UNPAID" -> log.info("Unpaid leave approved for employeeId={}", request.getEmployeeId());
                default -> {
                    if (balance.getCasualLeaveRemaining() < requestedDays) {
                        throw new IllegalStateException("Cannot approve: Insufficient casual leave balance.");
                    }
                    balance.setCasualLeaveUsed(balance.getCasualLeaveUsed() + requestedDays);
                }
            }
            leaveBalanceRepository.save(balance);
        }

        return leaveRequestRepository.save(request);
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> getLeavesByEmployee(Long employeeId) {
        return leaveRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId);
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> getPendingForManager(Long managerId) {
        return leaveRequestRepository.findPendingForManager(managerId);
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> getAllPendingRequests() {
        return leaveRequestRepository.findByStatusOrderByCreatedAtDesc("PENDING");
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> getAllLeaveRequests() {
        return leaveRequestRepository.findAll();
    }

    @Transactional
    public void bulkGrantLeaves(String leaveType, int grantDays, List<Long> excludedEmployeeIds) {
        int currentYear = LocalDate.now().getYear();
        List<Employee> employees = employeeRepository.findAll();
        List<Long> exclusions = (excludedEmployeeIds != null) ? excludedEmployeeIds : List.of();

        for (Employee emp : employees) {
            if (exclusions.contains(emp.getId())) {
                log.info("Skipping bulk leave grant for excluded employee: {} (ID: {})", emp.getFirstName() + " " + emp.getLastName(), emp.getId());
                continue;
            }
            LeaveBalance balance = getOrCreateLeaveBalance(emp.getId(), currentYear);
            switch (leaveType.toUpperCase()) {
                case "SICK" -> balance.setSickLeaveQuota(balance.getSickLeaveQuota() + grantDays);
                case "EARNED" -> balance.setEarnedLeaveQuota(balance.getEarnedLeaveQuota() + grantDays);
                default -> balance.setCasualLeaveQuota(balance.getCasualLeaveQuota() + grantDays);
            }
            leaveBalanceRepository.save(balance);
        }
        log.info("Bulk leave grant completed: +{} days of {} for all non-excluded employees.", grantDays, leaveType);
    }

    @Transactional
    public LeaveRequest applyOnBehalfByHr(LeaveRequest request) {
        request.setId(null);
        request.setStatus("APPROVED");
        request.setRejectionReason("Applied directly by HR Admin");

        if (request.getStartDate() == null || request.getEndDate() == null) {
            request.setStartDate(LocalDate.now());
            request.setEndDate(LocalDate.now());
        }

        long days = ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) + 1;
        request.setTotalDays((int) days);

        if (request.getLeaveType() == null || request.getLeaveType().isBlank()) {
            request.setLeaveType("CASUAL");
        }

        int year = request.getStartDate().getYear();
        LeaveBalance balance = getOrCreateLeaveBalance(request.getEmployeeId(), year);
        switch (request.getLeaveType().toUpperCase()) {
            case "SICK" -> balance.setSickLeaveUsed(balance.getSickLeaveUsed() + (int) days);
            case "EARNED" -> balance.setEarnedLeaveUsed(balance.getEarnedLeaveUsed() + (int) days);
            case "WORK_FROM_HOME" -> balance.setWorkFromHomeUsed(balance.getWorkFromHomeUsed() + (int) days);
            default -> balance.setCasualLeaveUsed(balance.getCasualLeaveUsed() + (int) days);
        }
        leaveBalanceRepository.save(balance);

        return leaveRequestRepository.save(request);
    }

    @Transactional
    public LeaveBalance adjustEmployeeBalance(Long employeeId, String leaveType, int adjustmentDays) {
        int year = LocalDate.now().getYear();
        LeaveBalance balance = getOrCreateLeaveBalance(employeeId, year);

        switch (leaveType.toUpperCase()) {
            case "SICK" -> balance.setSickLeaveQuota(Math.max(0, balance.getSickLeaveQuota() + adjustmentDays));
            case "EARNED" -> balance.setEarnedLeaveQuota(Math.max(0, balance.getEarnedLeaveQuota() + adjustmentDays));
            case "WORK_FROM_HOME" -> balance.setWorkFromHomeQuota(balance.getWorkFromHomeQuota() + adjustmentDays);
            default -> balance.setCasualLeaveQuota(Math.max(0, balance.getCasualLeaveQuota() + adjustmentDays));
        }

        return leaveBalanceRepository.save(balance);
    }
}
