package com.example.hr_management_backend.features.leaves.service;

import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import com.example.hr_management_backend.features.leaves.event.LeaveApprovedEvent;
import com.example.hr_management_backend.features.leaves.event.LeaveWithdrawnEvent;
import com.example.hr_management_backend.features.leaves.dto.LeaveRequestDto;
import com.example.hr_management_backend.features.leaves.model.EmployeeLeaveQuota;
import com.example.hr_management_backend.features.leaves.model.LeaveBalance;
import com.example.hr_management_backend.features.leaves.model.LeaveRequest;
import com.example.hr_management_backend.features.leaves.repository.EmployeeLeaveQuotaRepository;
import com.example.hr_management_backend.features.leaves.repository.LeaveBalanceRepository;
import com.example.hr_management_backend.features.leaves.repository.LeaveRequestRepository;
import com.example.hr_management_backend.features.attendance.model.Attendance;
import com.example.hr_management_backend.features.attendance.repository.AttendanceRepository;
import com.example.hr_management_backend.features.settings.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeaveService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final EmployeeLeaveQuotaRepository employeeLeaveQuotaRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SettingsService settingsService;
    private final com.example.hr_management_backend.features.leaves.policy.LeavePolicyEngine leavePolicyEngine;
    private final com.example.hr_management_backend.features.employees.repository.EmployeeAuthorityRepository employeeAuthorityRepository;
    private final com.example.hr_management_backend.features.auth.repository.UserRepository userRepository;
    private final com.example.hr_management_backend.features.email.service.EmailOutboxService emailOutboxService;
    private final LeaveAccrualService leaveAccrualService;
    private final LeaveCarryoverService leaveCarryoverService;

    private String capturePolicySnapshot() {
        try {
            String mode = settingsService.getSetting("time_off_policy_mode");
            String cycle = settingsService.getSetting("time_off_cycle");
            String sbUnit = settingsService.getSetting("time_off_short_break_unit_limit");
            String eoUnit = settingsService.getSetting("time_off_early_out_unit_limit");
            String laUnit = settingsService.getSetting("time_off_late_arrival_unit_limit");
            String hourly = settingsService.getSetting("time_off_hourly_limit");
            
            return "{"
                    + "\"time_off_policy_mode\":\"" + (mode != null ? mode : "") + "\","
                    + "\"time_off_cycle\":\"" + (cycle != null ? cycle : "") + "\","
                    + "\"time_off_short_break_unit_limit\":\"" + (sbUnit != null ? sbUnit : "") + "\","
                    + "\"time_off_early_out_unit_limit\":\"" + (eoUnit != null ? eoUnit : "") + "\","
                    + "\"time_off_late_arrival_unit_limit\":\"" + (laUnit != null ? laUnit : "") + "\","
                    + "\"time_off_hourly_limit\":\"" + (hourly != null ? hourly : "") + "\""
                    + "}";
        } catch (Exception e) {
            log.error("Failed to capture policy snapshot", e);
            return "{}";
        }
    }

    /**
     * Fetches or creates a leave balance for the given employee and year.
     * Computes real-time pending leave requests to ensure remaining balances
     * dynamically decrement immediately when a leave application is submitted.
     * Integrates pro-rata accrual for mid-year joiners and carryover from previous year.
     */
    @Transactional
    public LeaveBalance getOrCreateLeaveBalance(Long employeeId, Integer year) {
        LeaveBalance balance = leaveBalanceRepository.findByEmployeeIdAndYear(employeeId, year)
                .orElseGet(() -> {
                    double defaultRhQuota = 2.0;
                    try {
                        String setting = settingsService.getSetting("restricted_holiday_allowance");
                        if (setting != null && !setting.isBlank()) {
                            defaultRhQuota = Double.parseDouble(setting);
                        }
                    } catch (Exception ignored) {}

                    LocalDate expiryDate = LocalDate.of(year, 3, 31);
                    boolean isExpired = LocalDate.now().isAfter(expiryDate);

                    LeaveBalance initial = LeaveBalance.builder()
                            .employeeId(employeeId)
                            .year(year)
                            .casualLeaveUsed(0.0)
                            .sickLeaveUsed(0.0)
                            .earnedLeaveUsed(0.0)
                            .workFromHomeQuota(0.0)
                            .workFromHomeUsed(0.0)
                            .restrictedHolidayUsed(0.0)
                            .carriedForwardLeaveQuota(0.0)
                            .carriedForwardLeaveUsed(0.0)
                            .carriedForwardExpiryDate(expiryDate)
                            .carriedForwardExpired(isExpired)
                            .build();

                    Employee emp = employeeRepository.findById(employeeId).orElse(null);
                    leaveAccrualService.applyAccrualToBalance(initial, emp, year, defaultRhQuota);

                    // Check if previous year has unused Earned Leave eligible for carry forward
                    if (year > 2020) {
                        leaveBalanceRepository.findByEmployeeIdAndYear(employeeId, year - 1).ifPresent(prev -> {
                            double unusedEarned = Math.max(0.0,
                                    (prev.getEarnedLeaveQuota() != null ? prev.getEarnedLeaveQuota() : 6.0)
                                            - (prev.getEarnedLeaveUsed() != null ? prev.getEarnedLeaveUsed() : 0.0));
                            if (unusedEarned > 0.0) {
                                double carryover = Math.min(LeaveCarryoverService.MAX_EARNED_LEAVE_CARRYOVER_DAYS, unusedEarned);
                                initial.setCarriedForwardLeaveQuota(carryover);
                                log.info("Auto-carried forward {} days of Earned Leave for employeeId={} from year {} into year {}",
                                        carryover, employeeId, year - 1, year);
                            }
                        });
                    }

                    try {
                        return leaveBalanceRepository.save(initial);
                    } catch (org.springframework.dao.DataIntegrityViolationException dive) {
                        return leaveBalanceRepository.findByEmployeeIdAndYear(employeeId, year).orElse(initial);
                    }
                });

        Employee emp = employeeRepository.findById(employeeId).orElse(null);
        leaveCarryoverService.checkAndApplyLazyCarryoverAndExpiry(balance, emp, year);

        // Single-trip aggregation query: fetch all pending sums grouped by type in 1 trip
        java.util.Map<String, Double> pendingMap = new java.util.HashMap<>();
        for (Object[] row : leaveRequestRepository.sumPendingLeavesGroupedByType(employeeId, year)) {
            if (row != null && row.length >= 2 && row[0] != null && row[1] != null) {
                String type = ((String) row[0]).toUpperCase().replace("_LEAVE", "");
                Double val = ((Number) row[1]).doubleValue();
                pendingMap.put(type, val);
            }
        }

        balance.setCasualLeavePending(pendingMap.getOrDefault("CASUAL", 0.0));
        balance.setSickLeavePending(pendingMap.getOrDefault("SICK", 0.0));
        balance.setEarnedLeavePending(pendingMap.getOrDefault("EARNED", 0.0));
        balance.setWorkFromHomePending(pendingMap.getOrDefault("WORK_FROM_HOME", 0.0));
        balance.setRestrictedHolidayPending(pendingMap.getOrDefault("RESTRICTED_HOLIDAY", 0.0));

        // Segregate pending Earned Leaves into pre-expiry and post-expiry buckets
        LocalDate expiryDate = balance.getEffectiveCarriedForwardExpiryDate();
        Double prePending = leaveRequestRepository.sumPendingEarnedLeavesPreExpiry(employeeId, year, expiryDate);
        Double postPending = leaveRequestRepository.sumPendingEarnedLeavesPostExpiry(employeeId, year, expiryDate);
        balance.setEarnedLeavePendingPreExpiry(prePending != null ? prePending : 0.0);
        balance.setEarnedLeavePendingPostExpiry(postPending != null ? postPending : 0.0);

        return balance;
    }

    /**
     * Deducts Earned Leave with priority. If leave dates fall strictly after March 31st (expiry date),
     * carried-forward leaves cannot be availed and deductions come exclusively from the current year quota.
     * Otherwise, unexpired carried-forward balance is deducted first (FIFO), and the remainder from the current year.
     */
    public void deductEarnedLeaveWithPriority(LeaveBalance balance, double daysToDeduct) {
        deductEarnedLeaveWithPriority(balance, daysToDeduct, null, null);
    }

    public void deductEarnedLeaveWithPriority(LeaveBalance balance, double daysToDeduct, LocalDate startDate, LocalDate endDate) {
        if (daysToDeduct <= 0) return;

        LocalDate expiryDate = balance.getEffectiveCarriedForwardExpiryDate();
        boolean strictlyAfterExpiry = (startDate != null && startDate.isAfter(expiryDate));

        if (strictlyAfterExpiry) {
            // Leave dates are strictly after March 31: cannot consume carried forward leaves!
            if (balance.getCurrentYearEarnedLeaveRemaining() < daysToDeduct) {
                throw new IllegalStateException("Cannot approve: Insufficient current year earned leave balance for dates after March 31st.");
            }
            balance.setEarnedLeaveUsed((balance.getEarnedLeaveUsed() != null ? balance.getEarnedLeaveUsed() : 0.0) + daysToDeduct);
            log.info("Deducted Earned Leave strictly from current year (post-expiry dates {} to {}): {} days for employeeId={}",
                    startDate, endDate, daysToDeduct, balance.getEmployeeId());
            return;
        }

        if (balance.getEarnedLeaveRemaining() < daysToDeduct) {
            throw new IllegalStateException("Cannot approve: Insufficient earned leave balance.");
        }

        double carriedRemaining = balance.getCarriedForwardLeaveRemaining();
        if (carriedRemaining > 0) {
            double deductFromCarried = Math.min(daysToDeduct, carriedRemaining);
            balance.setCarriedForwardLeaveUsed((balance.getCarriedForwardLeaveUsed() != null ? balance.getCarriedForwardLeaveUsed() : 0.0) + deductFromCarried);
            double remainder = daysToDeduct - deductFromCarried;
            if (remainder > 0) {
                balance.setEarnedLeaveUsed((balance.getEarnedLeaveUsed() != null ? balance.getEarnedLeaveUsed() : 0.0) + remainder);
            }
            log.info("Deducted Earned Leave with priority: {} from carried-forward, {} from current year for employeeId={}",
                    deductFromCarried, remainder, balance.getEmployeeId());
        } else {
            balance.setEarnedLeaveUsed((balance.getEarnedLeaveUsed() != null ? balance.getEarnedLeaveUsed() : 0.0) + daysToDeduct);
            log.info("Deducted Earned Leave: {} from current year for employeeId={}", daysToDeduct, balance.getEmployeeId());
        }
    }

    /**
     * Refunds Earned Leave.
     * If carried forward is expired, refunds go to current year first to prevent valid days from lapsing into an expired bucket.
     * If carried forward is active (before March 31), refunds restore carried forward balance first so employees can utilize them before expiry.
     */
    public void refundEarnedLeaveWithPriority(LeaveBalance balance, double daysToRefund) {
        if (daysToRefund <= 0) return;

        boolean isExpired = balance.isCarriedForwardCurrentlyExpired();

        if (isExpired) {
            // Carried forward has expired: refund current year first
            double currentYearUsed = balance.getEarnedLeaveUsed() != null ? balance.getEarnedLeaveUsed() : 0.0;
            if (currentYearUsed > 0) {
                double refundToCurrent = Math.min(daysToRefund, currentYearUsed);
                balance.setEarnedLeaveUsed(Math.max(0.0, currentYearUsed - refundToCurrent));
                double remainderRefund = daysToRefund - refundToCurrent;
                if (remainderRefund > 0) {
                    double carriedUsed = balance.getCarriedForwardLeaveUsed() != null ? balance.getCarriedForwardLeaveUsed() : 0.0;
                    balance.setCarriedForwardLeaveUsed(Math.max(0.0, carriedUsed - remainderRefund));
                }
                log.info("Refunded Earned Leave (expired CF): {} to current year, {} to carried-forward for employeeId={}",
                        refundToCurrent, remainderRefund, balance.getEmployeeId());
            } else {
                double carriedUsed = balance.getCarriedForwardLeaveUsed() != null ? balance.getCarriedForwardLeaveUsed() : 0.0;
                balance.setCarriedForwardLeaveUsed(Math.max(0.0, carriedUsed - daysToRefund));
                log.info("Refunded Earned Leave (expired CF): {} to carried-forward for employeeId={}", daysToRefund, balance.getEmployeeId());
            }
        } else {
            // Carried forward is unexpired: restore carried forward first so employee can still use it before March 31st
            double carriedUsed = balance.getCarriedForwardLeaveUsed() != null ? balance.getCarriedForwardLeaveUsed() : 0.0;
            if (carriedUsed > 0) {
                double refundToCarried = Math.min(daysToRefund, carriedUsed);
                balance.setCarriedForwardLeaveUsed(Math.max(0.0, carriedUsed - refundToCarried));
                double remainderRefund = daysToRefund - refundToCarried;
                if (remainderRefund > 0) {
                    balance.setEarnedLeaveUsed(Math.max(0.0, (balance.getEarnedLeaveUsed() != null ? balance.getEarnedLeaveUsed() : 0.0) - remainderRefund));
                }
                log.info("Refunded Earned Leave (active CF): {} to carried-forward, {} to current year for employeeId={}",
                        refundToCarried, remainderRefund, balance.getEmployeeId());
            } else {
                balance.setEarnedLeaveUsed(Math.max(0.0, (balance.getEarnedLeaveUsed() != null ? balance.getEarnedLeaveUsed() : 0.0) - daysToRefund));
                log.info("Refunded Earned Leave (active CF): {} to current year for employeeId={}", daysToRefund, balance.getEmployeeId());
            }
        }
    }

    @Transactional
    @CacheEvict(value = "leave_balances", allEntries = true)
    public LeaveRequest applyForLeave(LeaveRequest request) {
        String email = "hr@company.com"; // default fallback for tests
        if (request.getEmployeeId() != null) {
            email = employeeRepository.findById(request.getEmployeeId())
                    .map(Employee::getEmail)
                    .orElse("hr@company.com");
        }
        return applyForLeave(request, email);
    }

    @Transactional
    @CacheEvict(value = "leave_balances", allEntries = true)
    public LeaveRequest applyForLeave(LeaveRequest request, String actorEmail) {
        Employee employee = employeeRepository.findByEmail(actorEmail)
                .orElseThrow(() -> new RuntimeException("Employee not found for email: " + actorEmail));
        
        // Enforce identity - ignore any forged employeeId in the payload
        request.setEmployeeId(employee.getId());

        request.setId(null);
        request.setStatus("PENDING");

        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new IllegalArgumentException("Start date and end date are required");
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }
        if (request.getStartDate().getYear() != request.getEndDate().getYear()) {
            throw new IllegalArgumentException("Leave requests cannot span across calendar years (Dec 31 / Jan 1). Please submit two separate requests for each respective year.");
        }

        String type = request.getLeaveType() != null ? request.getLeaveType().toUpperCase().replaceAll("[\\s\\-_]+", "_") : "";
        boolean isTimeBased = type.contains("SHORT_BREAK") || type.contains("SHORT_LEAVE")
                || type.contains("EARLY_OUT") || type.contains("EARLY_LEAVE") || type.contains("LATE_ARRIVAL");
        request.setIsTimeBased(isTimeBased);

        // Server-side authoritative calculation of totalDays
        double daysToApply = isTimeBased ? 0.0 : computeTotalDays(
                request.getStartDate(), request.getEndDate(),
                request.getStartSession(), request.getEndSession(), request.getTotalDays());
        request.setTotalDays(daysToApply);

        // Normalize session names for half-day requests so DB and rules have unambiguous state
        if (request.getStartDate().equals(request.getEndDate()) && daysToApply == 0.5) {
            if (isSession2(request.getStartSession()) || isSession2(request.getEndSession())) {
                request.setStartSession("Session 2");
                request.setEndSession("Session 2");
            } else {
                request.setStartSession("Session 1");
                request.setEndSession("Session 1");
            }
        }

        // Dynamically resolve assigned manager for leave routing
        if (employee.getManager() != null) {
            request.setApprovedBy(employee.getManager().getId());
            log.info("Leave request routed dynamically to assigned manager: {} (ID: {})",
                    employee.getManager().getFirstName() + " " + employee.getManager().getLastName(), employee.getManager().getId());
        }

        if (request.getLeaveType() == null || request.getLeaveType().isBlank()) {
            request.setLeaveType("CASUAL");
        }

        // Execute pluggable LeavePolicyEngine rules
        leavePolicyEngine.validateRequest(request, employee);
        leavePolicyEngine.computeDeductions(request);

        // Capture policy snapshot for ALL leave types (audit trail)
        request.setPolicySnapshot(capturePolicySnapshot());

        // Validate balance for non-time-based leave types
        if (!isTimeBased) {
            int currentYear = request.getStartDate().getYear();
            String reqType = request.getLeaveType().toUpperCase();
            double remaining = 0.0;

            if ("UNPAID".equals(reqType) || "WORK_FROM_HOME".equals(reqType) || "WFH".equals(reqType)) {
                // Serialize submission to prevent concurrent duplicate requests on identical dates
                employeeRepository.findByIdForUpdate(request.getEmployeeId());
                remaining = 999.0;
            } else {
                // Check if special leave type exists in dynamic EmployeeLeaveQuota table with PESSIMISTIC_WRITE lock
                var specialQuota = employeeLeaveQuotaRepository.findByEmployeeIdAndYearAndLeaveTypeForUpdate(request.getEmployeeId(), currentYear, reqType);
                if (specialQuota.isPresent()) {
                    remaining = specialQuota.get().getRemaining();
                } else if (isDynamicQuotaType(reqType)) {
                    remaining = 0.0;
                } else {
                    // PESSIMISTIC LOCK: Lock leave balance row before checking quotas to prevent concurrent quota bypass
                    leaveBalanceRepository.findByEmployeeIdAndYearForUpdate(request.getEmployeeId(), currentYear)
                            .orElseGet(() -> getOrCreateLeaveBalance(request.getEmployeeId(), currentYear));
                    
                    LeaveBalance balance = getOrCreateLeaveBalance(request.getEmployeeId(), currentYear);
                    String normType = reqType.replaceAll("_LEAVE$", "");
                    remaining = switch (normType) {
                        case "SICK" -> balance.getSickLeaveRemaining();
                        case "EARNED" -> {
                            // If leave dates fall strictly after March 31st, carried forward leaves cannot be used
                            LocalDate expiry = balance.getEffectiveCarriedForwardExpiryDate();
                            if (request.getStartDate().isAfter(expiry)) {
                                yield balance.getCurrentYearEarnedLeaveRemaining();
                            } else {
                                yield balance.getEarnedLeaveRemaining();
                            }
                        }
                        case "WORK_FROM_HOME", "WFH" -> 999.0;
                        case "RESTRICTED_HOLIDAY", "RESTRICTED" -> balance.getRestrictedHolidayRemaining();
                        default -> balance.getCasualLeaveRemaining();
                    };
                }
            }

            // getCasualLeaveRemaining() / getSickLeaveRemaining() / getEarnedLeaveRemaining()
            // already subtract pending leaves (set in getOrCreateLeaveBalance).
            // So `remaining` here is already the EFFECTIVE available balance.
            if (daysToApply > remaining) {
                throw new IllegalStateException("Insufficient leave balance (including pending requests). Effective Available: " + Math.max(0.0, remaining) + " days.");
            }
        } else {
            // PESSIMISTIC LOCK: Lock employee row to serialize permission quota evaluation and prevent concurrent submission race conditions
            if (request.getEmployeeId() != null) {
                employeeRepository.findByIdForUpdate(request.getEmployeeId());
            }
            // Enforce admin-configured time-off policies for short breaks, early outs, and late arrivals
            validateTimeBasedPolicy(request, type);
        }

        LeaveRequest saved = leaveRequestRepository.save(request);
        try {
            if (employee.getManager() != null && employee.getManager().getEmail() != null) {
                emailOutboxService.sendLeaveAppliedNotification(
                        employee.getManager().getEmail(),
                        employee.getManager().getFirstName() + " " + employee.getManager().getLastName(),
                        employee.getName(),
                        saved.getLeaveType(),
                        saved.getStartDate().toString(),
                        saved.getEndDate().toString(),
                        saved.getReason()
                );
            }
        } catch (Exception ex) {
            log.warn("[Leave Application] Failed to enqueue manager notification: {}", ex.getMessage());
        }
        return saved;
    }


    /**
     * Approves or rejects a leave request.
     * Uses Pessimistic DB Locking on LeaveBalance to prevent race conditions during deduction.
     */
    @Transactional
    @CacheEvict(value = "leave_balances", allEntries = true)
    public LeaveRequest updateStatus(Long requestId, String status, String rejectionReason, Long approverId) {
        String email = "admin@company.com"; // default fallback for tests & super admin
        if (approverId != null) {
            email = employeeRepository.findById(approverId)
                    .map(Employee::getEmail)
                    .orElse("admin@company.com");
        }
        return updateStatus(requestId, status, rejectionReason, email);
    }

    @Transactional
    @CacheEvict(value = "leave_balances", allEntries = true)
    public LeaveRequest updateStatus(Long requestId, String status, String rejectionReason, String actorEmail) {
        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Leave request not found with id: " + requestId));

        String previousStatus = request.getStatus() != null ? request.getStatus().toUpperCase() : "PENDING";
        String newStatus = status.toUpperCase();

        if (previousStatus.equals(newStatus)) {
            return request;
        }

        if ("CANCELLED".equalsIgnoreCase(previousStatus)
                || "WITHDRAWN".equalsIgnoreCase(previousStatus)
                || "REJECTED".equalsIgnoreCase(previousStatus)) {
            throw new IllegalStateException("Cannot change status of a leave request that is already in terminal state: " + previousStatus);
        }

        request.setStatus(newStatus);
        request.setRejectionReason(rejectionReason);

        Employee actor = employeeRepository.findByEmail(actorEmail).orElse(null);
        Employee targetEmployee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new RuntimeException("Leave requester not found"));

        if (actor == null) {
            // Check if actor is system Super Admin User without an employee record, or system admin email
            boolean isSysAdmin = "admin@company.com".equalsIgnoreCase(actorEmail);
            if (!isSysAdmin) {
                var userOpt = userRepository.findByEmail(actorEmail);
                isSysAdmin = userOpt.isPresent() && (
                        "SUPER_ADMIN".equalsIgnoreCase(userOpt.get().getRole()) ||
                        "ROLE_SUPER_ADMIN".equalsIgnoreCase(userOpt.get().getRole()) ||
                        "ADMIN".equalsIgnoreCase(userOpt.get().getRole()) ||
                        "ROLE_ADMIN".equalsIgnoreCase(userOpt.get().getRole()) ||
                        "ADMIN".equalsIgnoreCase(userOpt.get().getSystemRole()) ||
                        "SUPER_ADMIN".equalsIgnoreCase(userOpt.get().getSystemRole())
                );
            }
            if (!isSysAdmin) {
                throw new RuntimeException("Actor not found: " + actorEmail);
            }
            request.setApprovedBy(null);
        } else {
            // Strictly forbid self-approvals under any circumstances
            if (actor.getId().equals(targetEmployee.getId())) {
                throw new org.springframework.security.access.AccessDeniedException("Cannot approve or reject your own leave request.");
            }

            boolean isAuthorized = "HR".equalsIgnoreCase(actor.getRole())
                    || "SUPER_ADMIN".equalsIgnoreCase(actor.getRole())
                    || "ADMIN".equalsIgnoreCase(actor.getSystemRole())
                    || "SUPER_ADMIN".equalsIgnoreCase(actor.getSystemRole())
                    || employeeAuthorityRepository.existsByEmployeeIdAndAuthority(actor.getId(), "LEAVE_APPROVE_ALL");

            if (!isAuthorized && targetEmployee.getManager() != null) {
                isAuthorized = actor.getId().equals(targetEmployee.getManager().getId());
            }

            if (!isAuthorized) {
                throw new org.springframework.security.access.AccessDeniedException("Not authorized to update this leave request.");
            }

            request.setApprovedBy(actor.getId());
        }

        // Dedicated Handler for CANCEL_REQUESTED Adjudication
        if ("CANCEL_REQUESTED".equalsIgnoreCase(previousStatus)) {
            if ("CANCELLED".equalsIgnoreCase(newStatus) || "REJECTED".equalsIgnoreCase(newStatus)) {
                // Manager agreed to cancel: refund deducted days and clear attendance
                refundApprovedLeaveDays(request);
                request.setStatus("CANCELLED");
                request.setRejectionReason("Cancellation approved by manager: " + (rejectionReason != null ? rejectionReason : ""));
                LeaveRequest saved = leaveRequestRepository.save(request);
                eventPublisher.publishEvent(new LeaveWithdrawnEvent(
                        saved.getEmployeeId(), saved.getStartDate(), saved.getEndDate(), saved.getLeaveType()));
                try {
                    if (targetEmployee != null && targetEmployee.getEmail() != null) {
                        emailOutboxService.sendLeaveDecisionNotification(
                                targetEmployee.getEmail(),
                                targetEmployee.getName(),
                                saved.getLeaveType(),
                                saved.getStartDate().toString(),
                                saved.getEndDate().toString(),
                                saved.getStatus(),
                                rejectionReason
                        );
                    }
                } catch (Exception ex) {
                    log.warn("[Leave Decision] Failed to enqueue employee decision email: {}", ex.getMessage());
                }
                return saved;
            } else if ("APPROVED".equalsIgnoreCase(newStatus)) {
                // Manager denied cancellation: leave remains approved, skip re-deducting balance
                request.setStatus("APPROVED");
                request.setRejectionReason("Cancellation rejected by manager: " + (rejectionReason != null ? rejectionReason : ""));
                return leaveRequestRepository.save(request);
            }
        }

        int year = request.getStartDate().getYear();
        double requestedDays = request.getTotalDays() != null ? request.getTotalDays() : 0.0;
        String reqType = request.getLeaveType() != null ? request.getLeaveType().toUpperCase() : "CASUAL";
        boolean isTimeBased = reqType.contains("SHORT") || reqType.contains("EARLY") || reqType.contains("LATE");

        if ("APPROVED".equalsIgnoreCase(newStatus)) {
            var specialQuotaOpt = employeeLeaveQuotaRepository.findByEmployeeIdAndYearAndLeaveTypeForUpdate(request.getEmployeeId(), year, reqType);

            if (specialQuotaOpt.isPresent()) {
                EmployeeLeaveQuota quota = specialQuotaOpt.get();
                if (quota.getRemaining() < requestedDays) {
                    throw new IllegalStateException("Cannot approve: Insufficient " + reqType + " leave balance.");
                }
                quota.setUsed(quota.getUsed() + requestedDays);
                employeeLeaveQuotaRepository.save(quota);
            } else if (isDynamicQuotaType(reqType)) {
                throw new IllegalStateException("Cannot approve: Employee has no allocated quota for " + reqType + ".");
            } else {
                // PESSIMISTIC LOCK: Lock leave balance row for update
                LeaveBalance balance = leaveBalanceRepository.findByEmployeeIdAndYearForUpdate(request.getEmployeeId(), year)
                        .orElseGet(() -> getOrCreateLeaveBalance(request.getEmployeeId(), year));

                String normType = reqType.replaceAll("_LEAVE$", "");
                switch (normType) {
                    case "SICK" -> {
                        if (balance.getSickLeaveRemaining() < requestedDays) {
                            throw new IllegalStateException("Cannot approve: Insufficient sick leave balance.");
                        }
                        balance.setSickLeaveUsed(balance.getSickLeaveUsed() + requestedDays);
                    }
                    case "EARNED" -> deductEarnedLeaveWithPriority(balance, requestedDays, request.getStartDate(), request.getEndDate());
                    case "WORK_FROM_HOME", "WFH" -> {
                        balance.setWorkFromHomeUsed(balance.getWorkFromHomeUsed() + requestedDays);
                    }
                    case "UNPAID" -> log.info("Unpaid leave approved for employeeId={}", request.getEmployeeId());
                    case "RESTRICTED_HOLIDAY", "RESTRICTED" -> {
                        if (balance.getRestrictedHolidayRemaining() < requestedDays) {
                            throw new IllegalStateException("Cannot approve: Insufficient restricted holiday balance.");
                        }
                        balance.setRestrictedHolidayUsed(balance.getRestrictedHolidayUsed() + requestedDays);
                    }
                    default -> {
                        if (balance.getCasualLeaveRemaining() < requestedDays) {
                            throw new IllegalStateException("Cannot approve: Insufficient casual leave balance.");
                        }
                        balance.setCasualLeaveUsed(balance.getCasualLeaveUsed() + requestedDays);
                    }
                }
                leaveBalanceRepository.save(balance);
            }
        } else if ("CANCELLED".equalsIgnoreCase(newStatus) || "REJECTED".equalsIgnoreCase(newStatus)) {
            // Revert/refund used balance ONLY if it was previously approved
            if ("APPROVED".equalsIgnoreCase(previousStatus)) {
                refundApprovedLeaveDays(request);
            }
        }

        LeaveRequest saved = leaveRequestRepository.save(request);

        if ("APPROVED".equalsIgnoreCase(saved.getStatus())) {
            eventPublisher.publishEvent(new LeaveApprovedEvent(
                    saved.getEmployeeId(),
                    saved.getStartDate(),
                    saved.getEndDate(),
                    saved.getLeaveType()
            ));
        } else if (("REJECTED".equalsIgnoreCase(saved.getStatus()) || "CANCELLED".equalsIgnoreCase(saved.getStatus()))
                && ("APPROVED".equalsIgnoreCase(previousStatus) || "CANCEL_REQUESTED".equalsIgnoreCase(previousStatus))) {
            eventPublisher.publishEvent(new LeaveWithdrawnEvent(
                    saved.getEmployeeId(),
                    saved.getStartDate(),
                    saved.getEndDate(),
                    saved.getLeaveType()
            ));
        }

        try {
            if (targetEmployee != null && targetEmployee.getEmail() != null) {
                emailOutboxService.sendLeaveDecisionNotification(
                        targetEmployee.getEmail(),
                        targetEmployee.getName(),
                        saved.getLeaveType(),
                        saved.getStartDate().toString(),
                        saved.getEndDate().toString(),
                        saved.getStatus(),
                        rejectionReason
                );
            }
        } catch (Exception ex) {
            log.warn("[Leave Decision] Failed to enqueue employee decision email: {}", ex.getMessage());
        }

        return saved;
    }

    private java.util.Map<Long, Employee> getEmployeesMapForRequests(List<LeaveRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        java.util.Set<Long> empIds = requests.stream()
                .flatMap(req -> java.util.stream.Stream.of(req.getEmployeeId(), req.getApprovedBy()))
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (empIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        return employeeRepository.findAllById(empIds).stream()
                .collect(java.util.stream.Collectors.toMap(Employee::getId, e -> e, (e1, e2) -> e1));
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestDto> getLeavesByEmployeeDto(Long employeeId) {
        List<LeaveRequest> requests = leaveRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId);
        java.util.Map<Long, Employee> empMap = getEmployeesMapForRequests(requests);
        return requests.stream().map(req -> mapToDto(req, empMap)).toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> getLeavesByEmployee(Long employeeId) {
        return leaveRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId);
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestDto> getPendingForManager(Long managerId) {
        List<LeaveRequest> requests = leaveRequestRepository.findPendingForManager(managerId);
        java.util.Map<Long, Employee> empMap = getEmployeesMapForRequests(requests);
        return requests.stream().map(req -> mapToDto(req, empMap)).toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestDto> getAllPendingRequests() {
        List<LeaveRequest> requests = leaveRequestRepository.findAllPendingOrCancelRequested();
        java.util.Map<Long, Employee> empMap = getEmployeesMapForRequests(requests);
        return requests.stream().map(req -> mapToDto(req, empMap)).toList();
    }

    public LeaveRequestDto mapToDto(LeaveRequest req, java.util.Map<Long, Employee> empMap) {
        Employee emp = empMap != null ? empMap.get(req.getEmployeeId()) : null;
        String empName = (emp != null) ? (emp.getFirstName() + " " + emp.getLastName()) : "Employee #" + req.getEmployeeId();
        String empEmail = (emp != null) ? emp.getEmail() : "";
        String empDept = (emp != null) ? emp.getDepartmentCategory() : "General";
        String empDesig = (emp != null) ? emp.getDesignation() : "Employee";

        Employee approver = (req.getApprovedBy() != null && empMap != null) ? empMap.get(req.getApprovedBy()) : null;
        String approverName = (approver != null) ? (approver.getFirstName() + " " + approver.getLastName()) : null;

        return com.example.hr_management_backend.features.leaves.dto.LeaveRequestDto.builder()
                .id(req.getId())
                .employeeId(req.getEmployeeId())
                .employeeName(empName)
                .employeeEmail(empEmail)
                .employeeDepartment(empDept)
                .employeeDesignation(empDesig)
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .leaveType(req.getLeaveType())
                .totalDays(req.getTotalDays())
                .isTimeBased(req.getIsTimeBased())
                .startSession(req.getStartSession())
                .endSession(req.getEndSession())
                .reason(req.getReason())
                .status(req.getStatus())
                .rejectionReason(req.getRejectionReason())
                .approvedBy(req.getApprovedBy())
                .approverName(approverName)
                .startTime(req.getStartTime())
                .endTime(req.getEndTime())
                .documentUrl(req.getDocumentUrl())
                .ccEmails(req.getCcEmails())
                .policySnapshot(req.getPolicySnapshot())
                .createdAt(req.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> getAllLeaveRequests() {
        return leaveRequestRepository.findAll();
    }

    @Transactional
    @CacheEvict(value = "leave_balances", allEntries = true)
    public void bulkGrantLeaves(String leaveType, int grantDays, List<Long> excludedEmployeeIds) {
        int currentYear = LocalDate.now().getYear();
        List<Employee> employees = employeeRepository.findAll();
        List<Long> exclusions = (excludedEmployeeIds != null) ? excludedEmployeeIds : List.of();

        java.util.Map<Long, LeaveBalance> existingBalances = leaveBalanceRepository.findByYear(currentYear).stream()
                .collect(java.util.stream.Collectors.toMap(LeaveBalance::getEmployeeId, b -> b, (b1, b2) -> b1));

        List<LeaveBalance> balancesToSave = new ArrayList<>();
        for (Employee emp : employees) {
            if (exclusions.contains(emp.getId())) {
                log.info("Skipping bulk leave grant for excluded employee: {} (ID: {})", emp.getFirstName() + " " + emp.getLastName(), emp.getId());
                continue;
            }
            LeaveBalance balance = existingBalances.get(emp.getId());
            if (balance == null) {
                balance = getOrCreateLeaveBalance(emp.getId(), currentYear);
            }
            switch (leaveType.toUpperCase()) {
                case "SICK" -> balance.setSickLeaveQuota(balance.getSickLeaveQuota() + grantDays);
                case "EARNED" -> balance.setEarnedLeaveQuota(balance.getEarnedLeaveQuota() + grantDays);
                default -> balance.setCasualLeaveQuota(balance.getCasualLeaveQuota() + grantDays);
            }
            balancesToSave.add(balance);
        }
        if (!balancesToSave.isEmpty()) {
            leaveBalanceRepository.saveAll(balancesToSave);
        }
        log.info("Bulk leave grant completed: +{} days of {} for all non-excluded employees.", grantDays, leaveType);
    }

    @Transactional
    @CacheEvict(value = "leave_balances", allEntries = true)
    public LeaveRequest applyOnBehalfByHr(LeaveRequest request) {
        request.setId(null);
        request.setStatus("APPROVED");
        request.setRejectionReason("Applied directly by HR Admin");

        if (request.getStartDate() == null || request.getEndDate() == null) {
            request.setStartDate(LocalDate.now());
            request.setEndDate(LocalDate.now());
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }
        if (request.getStartDate().getYear() != request.getEndDate().getYear()) {
            throw new IllegalArgumentException("Leave requests cannot span across calendar years (Dec 31 / Jan 1). Please submit two separate requests for each respective year.");
        }

        String onBehalfType = request.getLeaveType() != null ? request.getLeaveType().toUpperCase().replaceAll("[ -]", "_") : "";
        boolean isTimeBased = onBehalfType.contains("SHORT_BREAK") || onBehalfType.contains("SHORT_LEAVE")
                || onBehalfType.contains("EARLY_OUT") || onBehalfType.contains("EARLY_LEAVE") || onBehalfType.contains("LATE_ARRIVAL");
        request.setIsTimeBased(isTimeBased);

        // Server-side authoritative totalDays calculation
        double daysToApply = isTimeBased ? 0.0 : computeTotalDays(
                request.getStartDate(), request.getEndDate(),
                request.getStartSession(), request.getEndSession(), request.getTotalDays());
        request.setTotalDays(daysToApply);

        // Explicitly stamp normalized sessions for half-day requests
        if (request.getStartDate().equals(request.getEndDate()) && daysToApply == 0.5) {
            if (isSession2(request.getStartSession()) || isSession2(request.getEndSession())) {
                request.setStartSession("Session 2");
                request.setEndSession("Session 2");
            } else {
                request.setStartSession("Session 1");
                request.setEndSession("Session 1");
            }
        }

        // Execute pluggable LeavePolicyEngine rules
        Employee emp = employeeRepository.findById(request.getEmployeeId()).orElse(null);
        leavePolicyEngine.validateRequest(request, emp);

        if (request.getLeaveType() == null || request.getLeaveType().isBlank()) {
            request.setLeaveType("CASUAL");
        }

        // Capture policy snapshot for audit trail
        request.setPolicySnapshot(capturePolicySnapshot());

        int year = request.getStartDate().getYear();

        // FIX: Use PESSIMISTIC_WRITE lock — same as updateStatus — to prevent concurrent deduction race condition
        LeaveBalance balance = leaveBalanceRepository.findByEmployeeIdAndYearForUpdate(request.getEmployeeId(), year)
                .orElseGet(() -> getOrCreateLeaveBalance(request.getEmployeeId(), year));

        if (!isTimeBased) {
            String rawType = request.getLeaveType().toUpperCase();

            // Tier 1: Dynamic quota types (COMP_OFF, MATERNITY, PATERNITY, BEREAVEMENT, BIRTHDAY …)
            //         These are tracked in EmployeeLeaveQuota with PESSIMISTIC_WRITE lock
            var specialQuotaOpt = employeeLeaveQuotaRepository
                    .findByEmployeeIdAndYearAndLeaveTypeForUpdate(request.getEmployeeId(), year, rawType);

            if (specialQuotaOpt.isPresent()) {
                EmployeeLeaveQuota quota = specialQuotaOpt.get();
                if (quota.getRemaining() < daysToApply) {
                    throw new IllegalStateException(
                            "Cannot apply: Insufficient " + rawType + " leave balance for employee.");
                }
                quota.setUsed(quota.getUsed() + daysToApply);
                employeeLeaveQuotaRepository.save(quota);
            } else if (isDynamicQuotaType(rawType)) {
                // Dynamic type but no quota row has been allocated yet
                throw new IllegalStateException(
                        "Cannot apply: Employee has no allocated quota for " + rawType + ".");
            } else {
                // Tier 2: Standard balance-table types
                String normType = rawType.replaceAll("_LEAVE$", "");
                switch (normType) {
                    case "SICK" -> {
                        if (balance.getSickLeaveRemaining() < daysToApply) {
                            throw new IllegalStateException("Cannot apply: Insufficient sick leave balance for employee.");
                        }
                        balance.setSickLeaveUsed(balance.getSickLeaveUsed() + daysToApply);
                    }
                    case "EARNED" -> deductEarnedLeaveWithPriority(balance, daysToApply, request.getStartDate(), request.getEndDate());
                    case "WORK_FROM_HOME", "WFH" -> balance.setWorkFromHomeUsed(balance.getWorkFromHomeUsed() + daysToApply);
                    case "UNPAID" -> log.info("Unpaid leave applied on behalf for employeeId={}", request.getEmployeeId());
                    case "RESTRICTED_HOLIDAY", "RESTRICTED" -> {
                        if (balance.getRestrictedHolidayRemaining() < daysToApply) {
                            throw new IllegalStateException("Cannot apply: Insufficient restricted holiday balance for employee.");
                        }
                        balance.setRestrictedHolidayUsed(balance.getRestrictedHolidayUsed() + daysToApply);
                    }
                    default -> {
                        if (balance.getCasualLeaveRemaining() < daysToApply) {
                            throw new IllegalStateException("Cannot apply: Insufficient casual leave balance for employee.");
                        }
                        balance.setCasualLeaveUsed(balance.getCasualLeaveUsed() + daysToApply);
                    }
                }
                leaveBalanceRepository.save(balance);
            }
        } else {
            // PESSIMISTIC LOCK: Lock employee row to serialize permission quota evaluation
            if (request.getEmployeeId() != null) {
                employeeRepository.findByIdForUpdate(request.getEmployeeId());
            }
            // Enforce admin-configured time-off policies for short breaks, early outs, and late arrivals
            validateTimeBasedPolicy(request, onBehalfType);
        }

        LeaveRequest saved = leaveRequestRepository.save(request);

        eventPublisher.publishEvent(new LeaveApprovedEvent(
                saved.getEmployeeId(),
                saved.getStartDate(),
                saved.getEndDate(),
                saved.getLeaveType()
        ));

        return saved;
    }

    /**
     * Withdraws a leave request:
     * - PENDING / IN_REVIEW leaves can be withdrawn by employees at any time.
     * - APPROVED leaves cannot be withdrawn by employees (must be cancelled/withdrawn administratively by HR/Admin).
     */
    @Transactional
    @CacheEvict(value = "leave_balances", allEntries = true)
    public LeaveRequest withdrawApprovedLeave(Long requestId, String actorEmail) {
        Long actorId = null;
        if (actorEmail != null && !"admin@company.com".equalsIgnoreCase(actorEmail)) {
            actorId = employeeRepository.findByEmail(actorEmail)
                    .map(Employee::getId)
                    .orElse(null);
        }
        return withdrawApprovedLeave(requestId, actorId);
    }

    @Transactional
    @CacheEvict(value = "leave_balances", allEntries = true)
    public LeaveRequest withdrawApprovedLeave(Long requestId, Long actorId) {
        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Leave request not found: " + requestId));

        String status = request.getStatus() != null ? request.getStatus().toUpperCase() : "PENDING";

        if ("WITHDRAWN".equals(status) || "CANCELLED".equals(status) || "REJECTED".equals(status)) {
            throw new IllegalStateException("Leave request is already " + status);
        }

        if ("APPROVED".equals(status)) {
            boolean isSelfEmployee = actorId != null && actorId.equals(request.getEmployeeId());
            if (isSelfEmployee) {
                throw new IllegalStateException("Approved leaves cannot be withdrawn by employees. Please contact your manager or HR.");
            }

            String type = request.getLeaveType() != null ? request.getLeaveType().toUpperCase() : "";
            boolean isTimeBased = type.contains("SHORT") || type.contains("EARLY");

            if (!isTimeBased) {
                int year = request.getStartDate().getYear();
                double daysToCredit = request.getTotalDays() != null ? request.getTotalDays() : 0.0;

                var specialQuotaOpt = employeeLeaveQuotaRepository.findByEmployeeIdAndYearAndLeaveTypeForUpdate(request.getEmployeeId(), year, type);
                if (specialQuotaOpt.isPresent()) {
                    EmployeeLeaveQuota quota = specialQuotaOpt.get();
                    quota.setUsed(Math.max(0, quota.getUsed() - daysToCredit));
                    employeeLeaveQuotaRepository.save(quota);
                } else {
                    LeaveBalance balance = leaveBalanceRepository.findByEmployeeIdAndYearForUpdate(request.getEmployeeId(), year)
                            .orElseThrow(() -> new RuntimeException("Leave balance not found for employee: " + request.getEmployeeId()));

                    String normType = type.replaceAll("_LEAVE$", "");
                    switch (normType) {
                        case "SICK" -> balance.setSickLeaveUsed(Math.max(0, balance.getSickLeaveUsed() - daysToCredit));
                        case "EARNED" -> refundEarnedLeaveWithPriority(balance, daysToCredit);
                        case "WORK_FROM_HOME", "WFH" -> balance.setWorkFromHomeUsed(Math.max(0, balance.getWorkFromHomeUsed() - daysToCredit));
                        case "UNPAID" -> log.info("Unpaid leave withdrawn — no balance change for employeeId={}", request.getEmployeeId());
                        case "RESTRICTED_HOLIDAY", "RESTRICTED" -> balance.setRestrictedHolidayUsed(Math.max(0, balance.getRestrictedHolidayUsed() - daysToCredit));
                        default -> balance.setCasualLeaveUsed(Math.max(0, balance.getCasualLeaveUsed() - daysToCredit));
                    }
                    leaveBalanceRepository.save(balance);
                }
                log.info("Balance credited back: {} days of {} for employeeId={}", daysToCredit, type, request.getEmployeeId());
            }
        }

        request.setStatus("WITHDRAWN");
        request.setRejectionReason("APPROVED".equals(status)
                ? "Withdrawn administratively (actorId=" + actorId + ")"
                : "Withdrawn by employee (actorId=" + actorId + ")");
        LeaveRequest saved = leaveRequestRepository.save(request);

        leavePolicyEngine.handleStatusChange(saved, status, "WITHDRAWN");
        eventPublisher.publishEvent(new LeaveWithdrawnEvent(
                saved.getEmployeeId(),
                saved.getStartDate(),
                saved.getEndDate(),
                saved.getLeaveType()
        ));

        return saved;
    }


    @Transactional
    @CacheEvict(value = "leave_balances", allEntries = true)
    public LeaveBalance adjustEmployeeBalance(Long employeeId, String leaveType, double adjustmentDays) {
        int year = LocalDate.now().getYear();
        String upperType = leaveType.toUpperCase();

        if (isDynamicQuotaType(upperType)) {
            EmployeeLeaveQuota quota = employeeLeaveQuotaRepository
                    .findByEmployeeIdAndYearAndLeaveTypeForUpdate(employeeId, year, upperType)
                    .orElseGet(() -> EmployeeLeaveQuota.builder()
                            .employeeId(employeeId)
                            .year(year)
                            .leaveType(upperType)
                            .quota(0.0)
                            .used(0.0)
                            .build());
            quota.setQuota(Math.max(0.0, quota.getQuota() + adjustmentDays));
            employeeLeaveQuotaRepository.save(quota);
            return getOrCreateLeaveBalance(employeeId, year);
        }

        leaveBalanceRepository.findByEmployeeIdAndYearForUpdate(employeeId, year);
        LeaveBalance balance = getOrCreateLeaveBalance(employeeId, year);

        switch (upperType) {
            case "SICK" -> balance.setSickLeaveQuota(Math.max(0, balance.getSickLeaveQuota() + adjustmentDays));
            case "EARNED" -> balance.setEarnedLeaveQuota(Math.max(0, balance.getEarnedLeaveQuota() + adjustmentDays));
            case "WORK_FROM_HOME", "WFH" -> balance.setWorkFromHomeQuota(balance.getWorkFromHomeQuota() + adjustmentDays);
            case "RESTRICTED_HOLIDAY", "RESTRICTED" -> balance.setRestrictedHolidayQuota(Math.max(0, balance.getRestrictedHolidayQuota() + adjustmentDays));
            default -> balance.setCasualLeaveQuota(Math.max(0, balance.getCasualLeaveQuota() + adjustmentDays));
        }

        return leaveBalanceRepository.save(balance);
    }

    @Transactional
    @CacheEvict(value = "leave_balances", allEntries = true)
    public LeaveBalance adjustEmployeeBalance(Long employeeId, String leaveType, double adjustmentDays, String reason) {
        log.info("Adjusting balance for employee {} leaveType {} by {} days. Reason: {}", employeeId, leaveType, adjustmentDays, reason);
        return adjustEmployeeBalance(employeeId, leaveType, adjustmentDays);
    }

    /**
     * Validates that the caller has ownership (self) or privileged authority (Manager/HR/Admin)
     * to view the given employee's confidential leave requests and balance.
     */
    public void validateOwnershipOrPrivilege(Long targetEmployeeId, String callerEmail) {
        if (callerEmail == null || "admin@company.com".equalsIgnoreCase(callerEmail)) {
            return; // System Super Admin / test fallback
        }

        Employee caller = employeeRepository.findByEmail(callerEmail).orElse(null);
        if (caller == null) {
            var userOpt = userRepository.findByEmail(callerEmail);
            if (userOpt.isPresent() && (
                    "ADMIN".equalsIgnoreCase(userOpt.get().getRole())
                    || "ROLE_ADMIN".equalsIgnoreCase(userOpt.get().getRole())
                    || "SUPER_ADMIN".equalsIgnoreCase(userOpt.get().getRole())
                    || "ROLE_SUPER_ADMIN".equalsIgnoreCase(userOpt.get().getRole()))) {
                return;
            }
            throw new org.springframework.security.access.AccessDeniedException("Authenticated caller not found: " + callerEmail);
        }

        // Privileged roles can view any employee's records
        if ("SUPER_ADMIN".equalsIgnoreCase(caller.getRole())
                || "ADMIN".equalsIgnoreCase(caller.getRole())
                || "HR".equalsIgnoreCase(caller.getRole())
                || "SUPER_ADMIN".equalsIgnoreCase(caller.getSystemRole())
                || "ADMIN".equalsIgnoreCase(caller.getSystemRole())
                || employeeAuthorityRepository.existsByEmployeeIdAndAuthority(caller.getId(), "LEAVE_APPROVE_ALL")) {
            return;
        }

        // Self-access
        if (caller.getId().equals(targetEmployeeId)) {
            return;
        }

        // Manager accessing direct report
        Employee target = employeeRepository.findById(targetEmployeeId).orElse(null);
        if (target != null && target.getManager() != null && target.getManager().getId().equals(caller.getId())) {
            return;
        }

        throw new org.springframework.security.access.AccessDeniedException(
                "Access Denied: You do not have permission to view leave records for employee ID: " + targetEmployeeId);
    }

    /**
     * Validates that the caller is authorized to view the pending queue for the specified manager.
     */
    public void validateManagerAccess(Long targetManagerId, String callerEmail) {
        if (callerEmail == null || "admin@company.com".equalsIgnoreCase(callerEmail)) {
            return; // System Super Admin / test fallback
        }

        Employee caller = employeeRepository.findByEmail(callerEmail).orElse(null);
        if (caller == null) {
            var userOpt = userRepository.findByEmail(callerEmail);
            if (userOpt.isPresent() && (
                    "ADMIN".equalsIgnoreCase(userOpt.get().getRole())
                    || "ROLE_ADMIN".equalsIgnoreCase(userOpt.get().getRole())
                    || "SUPER_ADMIN".equalsIgnoreCase(userOpt.get().getRole())
                    || "ROLE_SUPER_ADMIN".equalsIgnoreCase(userOpt.get().getRole()))) {
                return;
            }
            throw new org.springframework.security.access.AccessDeniedException("Authenticated caller not found: " + callerEmail);
        }

        // Privileged roles can inspect any manager's pending queue
        if ("SUPER_ADMIN".equalsIgnoreCase(caller.getRole())
                || "ADMIN".equalsIgnoreCase(caller.getRole())
                || "HR".equalsIgnoreCase(caller.getRole())
                || "SUPER_ADMIN".equalsIgnoreCase(caller.getSystemRole())
                || "ADMIN".equalsIgnoreCase(caller.getSystemRole())
                || employeeAuthorityRepository.existsByEmployeeIdAndAuthority(caller.getId(), "LEAVE_APPROVE_ALL")) {
            return;
        }

        // The designated manager themselves
        if (caller.getId().equals(targetManagerId)) {
            return;
        }

        throw new org.springframework.security.access.AccessDeniedException(
                "Access Denied: You do not have permission to access pending queue for manager ID: " + targetManagerId);
    }

    public boolean isDynamicQuotaType(String type) {
        if (type == null) return false;
        String upper = type.toUpperCase().replaceAll("[\\s\\-_]+", "_").replaceAll("_LEAVE$", "");
        return upper.equals("COMP_OFF") || upper.equals("COMPOFF")
                || upper.equals("MATERNITY") || upper.equals("PATERNITY")
                || upper.equals("BEREAVEMENT") || upper.equals("BIRTHDAY");
    }

    /**
     * Validates short break / early out requests against admin-configured time-off policies.
     * Reads policy mode, cycle, and limits from the Settings table.
     */
    private void validateTimeBasedPolicy(LeaveRequest request, String normalizedType) {
        String policyMode = settingsService.getSetting("time_off_policy_mode");
        if (policyMode == null || policyMode.isBlank()) policyMode = "FLEXIBLE";

        // FLEXIBLE mode = no limits, admin/approver reviews manually
        if ("FLEXIBLE".equalsIgnoreCase(policyMode)) {
            log.info("Time-off policy mode is FLEXIBLE — skipping limit enforcement for employeeId={}", request.getEmployeeId());
            return;
        }

        // Calculate cycle date range
        String cycle = settingsService.getSetting("time_off_cycle");
        if (cycle == null || cycle.isBlank()) cycle = "Monthly";
        LocalDate[] cycleRange = computeCycleDateRange(request.getStartDate(), cycle);
        LocalDate cycleStart = cycleRange[0];
        LocalDate cycleEnd = cycleRange[1];

        boolean isShortBreak = normalizedType.contains("SHORT");
        boolean isEarlyOut = normalizedType.contains("EARLY");
        boolean isLateArrival = normalizedType.contains("LATE");

        if ("UNITWISE".equalsIgnoreCase(policyMode)) {
            // Unit-based: count number of requests (not hours)
            int shortBreakLimit = parseIntSetting("time_off_short_break_unit_limit", 2);
            int earlyOutLimit = parseIntSetting("time_off_early_out_unit_limit", 2);
            int lateArrivalLimit = parseIntSetting("time_off_late_arrival_unit_limit", 2);

            if (isShortBreak) {
                long currentCount = leaveRequestRepository.countTimeBasedRequestsInCycle(
                        request.getEmployeeId(), cycleStart, cycleEnd, "%SHORT%");
                if (currentCount >= shortBreakLimit) {
                    throw new IllegalStateException("Short break limit reached (" + shortBreakLimit + " per " + cycle.toLowerCase() + "). Used: " + currentCount);
                }
            } else if (isEarlyOut) {
                long currentCount = leaveRequestRepository.countTimeBasedRequestsInCycle(
                        request.getEmployeeId(), cycleStart, cycleEnd, "%EARLY%");
                if (currentCount >= earlyOutLimit) {
                    throw new IllegalStateException("Early out limit reached (" + earlyOutLimit + " per " + cycle.toLowerCase() + "). Used: " + currentCount);
                }
            } else if (isLateArrival) {
                long currentCount = leaveRequestRepository.countTimeBasedRequestsInCycle(
                        request.getEmployeeId(), cycleStart, cycleEnd, "%LATE%");
                if (currentCount >= lateArrivalLimit) {
                    throw new IllegalStateException("Late arrival limit reached (" + lateArrivalLimit + " per " + cycle.toLowerCase() + "). Used: " + currentCount);
                }
            }
        } else if ("HOURLY_SEPARATE".equalsIgnoreCase(policyMode) || "HOURLY_COMBINED".equalsIgnoreCase(policyMode)) {
            // Hour-based: aggregate actual duration in hours using startTime and endTime
            int hourlyLimit = parseIntSetting("time_off_hourly_limit", 4);

            double incomingHours = computeHoursBridged(request.getStartTime(), request.getEndTime());

            if ("HOURLY_COMBINED".equalsIgnoreCase(policyMode)) {
                double usedHours = computeTotalTimeBasedHours(request.getEmployeeId(), cycleStart, cycleEnd, null);
                if ((usedHours + incomingHours) > hourlyLimit) {
                    throw new IllegalStateException("Combined time-off hourly limit exceeded (" + hourlyLimit + " hrs per " + cycle.toLowerCase() + "). Current used: " + String.format("%.1f", usedHours) + " hrs, requested: " + String.format("%.1f", incomingHours) + " hrs.");
                }
            } else {
                String typePattern = isShortBreak ? "%SHORT%" : (isEarlyOut ? "%EARLY%" : "%LATE%");
                double usedHours = computeTotalTimeBasedHours(request.getEmployeeId(), cycleStart, cycleEnd, typePattern);
                if ((usedHours + incomingHours) > hourlyLimit) {
                    throw new IllegalStateException(normalizedType + " hourly limit exceeded (" + hourlyLimit + " hrs per " + cycle.toLowerCase() + "). Current used: " + String.format("%.1f", usedHours) + " hrs, requested: " + String.format("%.1f", incomingHours) + " hrs.");
                }
            }
        }

        log.info("Time-off policy validated: mode={}, cycle={}, type={}, employeeId={}",
                policyMode, cycle, normalizedType, request.getEmployeeId());
    }

    private double computeHoursBridged(LocalTime start, LocalTime end) {
        if (start == null || end == null || start.equals(end)) return 1.0;
        long mins = end.isAfter(start)
                ? ChronoUnit.MINUTES.between(start, end)
                : (ChronoUnit.MINUTES.between(start, LocalTime.MAX) + 1 + ChronoUnit.MINUTES.between(LocalTime.MIN, end));
        return mins > 0 ? mins / 60.0 : 1.0;
    }

    private double computeTotalTimeBasedHours(Long employeeId, LocalDate cycleStart, LocalDate cycleEnd, String typePattern) {
        List<LeaveRequest> requests = leaveRequestRepository.findTimeBasedRequestsInCycle(employeeId, cycleStart, cycleEnd, typePattern);
        double totalHours = 0.0;
        for (LeaveRequest req : requests) {
            totalHours += computeHoursBridged(req.getStartTime(), req.getEndTime());
        }
        return totalHours;
    }

    // ── Session normalization helpers ──────────────────────────────────────────
    // Accept: "SESSION_1", "Session 1", "session1", "S1", "MORNING", etc.
    private boolean isSession1(String session) {
        if (session == null) return false;
        String s = session.toUpperCase().replaceAll("[\\s_\\-]", "");
        return s.equals("SESSION1") || s.equals("S1") || s.equals("MORNING") || s.equals("1");
    }

    private boolean isSession2(String session) {
        if (session == null) return false;
        String s = session.toUpperCase().replaceAll("[\\s_\\-]", "");
        return s.equals("SESSION2") || s.equals("S2") || s.equals("AFTERNOON") || s.equals("2");
    }

    /**
     * Computes total days server-side taking session boundaries into account.
     * Examples:
     *   Day1(S1) - Day1(S2) = 1.0 (full day)
     *   Day1(S1) - Day1(S1) = 0.5 (half day)
     *   Day1(S2) - Day2(S1) = 1.0 (afternoon + morning)
     *   Day1(S1) - Day3(S2) = 3.0
     *   Day1(S2) - Day3(S1) = 2.0
     */
    double computeTotalDays(LocalDate start, LocalDate end, String startSession, String endSession) {
        return computeTotalDays(start, end, startSession, endSession, null);
    }

    double computeTotalDays(LocalDate start, LocalDate end, String startSession, String endSession, Double clientTotalDays) {
        if (start == null || end == null) return 1.0;

        if (start.equals(end)) {
            boolean isS1 = isSession1(startSession) || isSession1(endSession);
            boolean isS2 = isSession2(startSession) || isSession2(endSession);

            if (isS1 && isS2) {
                return 1.0; // Both sessions specified = Full Day
            }
            if (isS1 || isS2) {
                return 0.5; // Single session specified = Half Day
            }
            // Fallback: If client explicitly declared 0.5 days on a single-day leave
            if (clientTotalDays != null && clientTotalDays > 0.0 && clientTotalDays <= 0.5) {
                return 0.5;
            }
            return 1.0; // Unspecified default = full day
        }

        boolean startsS2 = isSession2(startSession) && !isSession1(startSession);
        boolean endsS1 = isSession1(endSession) && !isSession2(endSession);

        long rawDays = ChronoUnit.DAYS.between(start, end) + 1;
        double adjustment = 0.0;
        if (startsS2) adjustment -= 0.5;  // starts at afternoon, loses half a day
        if (endsS1) adjustment -= 0.5;    // ends at morning, loses half a day
        return Math.max(0.5, rawDays + adjustment);
    }

    private LocalDate[] computeCycleDateRange(LocalDate requestDate, String cycle) {
        return switch (cycle.toUpperCase()) {
            case "QUARTERLY" -> {
                int quarterStart = ((requestDate.getMonthValue() - 1) / 3) * 3 + 1;
                LocalDate start = LocalDate.of(requestDate.getYear(), quarterStart, 1);
                LocalDate end = start.plusMonths(3).minusDays(1);
                yield new LocalDate[]{start, end};
            }
            case "HALF-YEARLY", "HALF_YEARLY" -> {
                int halfStart = requestDate.getMonthValue() <= 6 ? 1 : 7;
                LocalDate start = LocalDate.of(requestDate.getYear(), halfStart, 1);
                LocalDate end = start.plusMonths(6).minusDays(1);
                yield new LocalDate[]{start, end};
            }
            case "YEARLY" -> {
                LocalDate start = LocalDate.of(requestDate.getYear(), 1, 1);
                LocalDate end = LocalDate.of(requestDate.getYear(), 12, 31);
                yield new LocalDate[]{start, end};
            }
            default -> { // MONTHLY
                YearMonth ym = YearMonth.from(requestDate);
                yield new LocalDate[]{ym.atDay(1), ym.atEndOfMonth()};
            }
        };
    }

    private int parseIntSetting(String key, int defaultValue) {
        try {
            String val = settingsService.getSetting(key);
            return (val != null && !val.isBlank()) ? Integer.parseInt(val) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Transactional
    public void clearAllLeaveData() {
        leaveRequestRepository.deleteAllInBatch();

        List<LeaveBalance> balances = leaveBalanceRepository.findAll();
        for (LeaveBalance b : balances) {
            b.setCasualLeaveUsed(0.0);
            b.setSickLeaveUsed(0.0);
            b.setEarnedLeaveUsed(0.0);
            b.setWorkFromHomeUsed(0.0);
            b.setRestrictedHolidayUsed(0.0);
            b.setCarriedForwardLeaveQuota(0.0);
            b.setCarriedForwardLeaveUsed(0.0);
            b.setCarriedForwardExpired(false);
        }
        leaveBalanceRepository.saveAll(balances);

        List<EmployeeLeaveQuota> quotas = employeeLeaveQuotaRepository.findAll();
        for (EmployeeLeaveQuota q : quotas) {
            q.setUsed(0.0);
        }
        employeeLeaveQuotaRepository.saveAll(quotas);

        List<Attendance> attendances = attendanceRepository.findAll();
        List<Attendance> leaveAttendances = attendances.stream()
                .filter(a -> "ON_LEAVE".equalsIgnoreCase(a.getStatus()) || "HALF_DAY_LEAVE".equalsIgnoreCase(a.getStatus()))
                .toList();
        attendanceRepository.deleteAllInBatch(leaveAttendances);
        log.info("Cleared ALL leave requests, reset all balances and leave attendance records.");
    }

    @Transactional
    @CacheEvict(value = "leave_balances", allEntries = true)
    public LeaveRequest cancelLeaveRequest(Long requestId, Long actorId) {
        String actorEmail = null;
        if (actorId != null) {
            actorEmail = employeeRepository.findById(actorId).map(Employee::getEmail).orElse(null);
        }
        return cancelLeaveRequest(requestId, actorEmail, null);
    }

    @Transactional
    @CacheEvict(value = "leave_balances", allEntries = true)
    public LeaveRequest cancelLeaveRequest(Long requestId, String actorEmail, String reason) {
        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Leave request not found with id: " + requestId));

        String oldStatus = request.getStatus() != null ? request.getStatus().toUpperCase() : "PENDING";
        if ("CANCELLED".equals(oldStatus) || "REJECTED".equals(oldStatus) || "WITHDRAWN".equals(oldStatus)) {
            throw new IllegalStateException("Leave request is already " + oldStatus);
        }

        Employee actor = (actorEmail != null) ? employeeRepository.findByEmail(actorEmail).orElse(null) : null;
        boolean isSysAdmin = false;
        if (actor == null && actorEmail != null) {
            isSysAdmin = "admin@company.com".equalsIgnoreCase(actorEmail)
                    || userRepository.findByEmail(actorEmail)
                    .map(u -> u.getRole() != null && (u.getRole().contains("ADMIN") || u.getRole().contains("SUPER_ADMIN")))
                    .orElse(false);
        }

        boolean isManagerOrAdmin = isSysAdmin || (actor != null && (
                "HR".equalsIgnoreCase(actor.getRole())
                || "SUPER_ADMIN".equalsIgnoreCase(actor.getRole())
                || "ADMIN".equalsIgnoreCase(actor.getSystemRole())
                || "SUPER_ADMIN".equalsIgnoreCase(actor.getSystemRole())
                || employeeAuthorityRepository.existsByEmployeeIdAndAuthority(actor.getId(), "LEAVE_APPROVE_ALL")
        ));

        Employee targetEmployee = employeeRepository.findById(request.getEmployeeId()).orElse(null);
        if (!isManagerOrAdmin && actor != null && targetEmployee != null && targetEmployee.getManager() != null) {
            isManagerOrAdmin = actor.getId().equals(targetEmployee.getManager().getId());
        }

        boolean isOwner = actor != null && actor.getId().equals(request.getEmployeeId());

        // Authorization check: Actor must be either the owner or a manager/admin
        if (!isOwner && !isManagerOrAdmin) {
            throw new org.springframework.security.access.AccessDeniedException("You are not authorized to cancel this leave request.");
        }

        // Two-Phase Workflow for APPROVED leaves:
        if ("APPROVED".equals(oldStatus)) {
            if (!isManagerOrAdmin) {
                // Employee self-initiating cancellation on an already APPROVED leave:
                // Cannot cancel directly and cannot self-refund used days! Must submit a Cancellation Request.
                request.setStatus("CANCEL_REQUESTED");
                if (reason != null && !reason.isBlank()) {
                    request.setReason((request.getReason() != null ? request.getReason() + " | Cancellation Reason: " : "Cancellation Reason: ") + reason);
                }
                LeaveRequest saved = leaveRequestRepository.save(request);
                log.info("Employee ID={} submitted cancellation request for approved leave ID={}", request.getEmployeeId(), requestId);

                // Notify manager of cancellation request
                try {
                    if (targetEmployee != null && targetEmployee.getManager() != null) {
                        Employee mgr = targetEmployee.getManager();
                        emailOutboxService.sendLeaveDecisionNotification(
                                mgr.getEmail(), mgr.getName(), request.getLeaveType(),
                                request.getStartDate().toString(), request.getEndDate().toString(),
                                "CANCEL_REQUESTED", (reason != null ? reason : "Employee requested cancellation of approved leave.")
                        );
                    }
                } catch (Exception ex) {
                    log.warn("Failed to notify manager of cancellation request: {}", ex.getMessage());
                }
                return saved;
            }

            // Manager / Admin direct cancellation of APPROVED leave: refund used days
            refundApprovedLeaveDays(request);
        } else if ("CANCEL_REQUESTED".equals(oldStatus)) {
            if (!isManagerOrAdmin) {
                throw new IllegalStateException("Cancellation has already been requested for this leave. Please await manager review.");
            }
            // Manager/Admin approves the cancellation request: refund used days
            refundApprovedLeaveDays(request);
        }

        request.setStatus("CANCELLED");
        LeaveRequest saved = leaveRequestRepository.save(request);

        leavePolicyEngine.handleStatusChange(saved, oldStatus, "CANCELLED");
        if (!Boolean.TRUE.equals(saved.getIsTimeBased())) {
            eventPublisher.publishEvent(new com.example.hr_management_backend.features.leaves.event.LeaveWithdrawnEvent(
                    saved.getEmployeeId(), saved.getStartDate(), saved.getEndDate(), saved.getLeaveType()
            ));
        }

        return saved;
    }

    private void refundApprovedLeaveDays(LeaveRequest request) {
        int year = request.getStartDate().getYear();
        double requestedDays = request.getTotalDays() != null ? request.getTotalDays() : 0.0;
        String reqType = request.getLeaveType() != null ? request.getLeaveType().toUpperCase() : "CASUAL";
        boolean isTimeBased = Boolean.TRUE.equals(request.getIsTimeBased());

        if (!isTimeBased && requestedDays > 0) {
            var specialQuotaOpt = employeeLeaveQuotaRepository.findByEmployeeIdAndYearAndLeaveTypeForUpdate(request.getEmployeeId(), year, reqType);
            if (specialQuotaOpt.isPresent()) {
                var quota = specialQuotaOpt.get();
                quota.setUsed(Math.max(0.0, quota.getUsed() - requestedDays));
                employeeLeaveQuotaRepository.save(quota);
            } else {
                LeaveBalance balance = leaveBalanceRepository.findByEmployeeIdAndYearForUpdate(request.getEmployeeId(), year)
                        .orElseGet(() -> getOrCreateLeaveBalance(request.getEmployeeId(), year));
                String normType = reqType.replaceAll("_LEAVE$", "");
                switch (normType) {
                    case "SICK" -> balance.setSickLeaveUsed(Math.max(0.0, balance.getSickLeaveUsed() - requestedDays));
                    case "EARNED" -> refundEarnedLeaveWithPriority(balance, requestedDays);
                    case "WORK_FROM_HOME", "WFH" -> balance.setWorkFromHomeUsed(Math.max(0.0, balance.getWorkFromHomeUsed() - requestedDays));
                    case "UNPAID" -> log.info("Unpaid leave cancelled — no balance change for employeeId={}", request.getEmployeeId());
                    case "RESTRICTED_HOLIDAY", "RESTRICTED" -> balance.setRestrictedHolidayUsed(Math.max(0.0, balance.getRestrictedHolidayUsed() - requestedDays));
                    default -> balance.setCasualLeaveUsed(Math.max(0.0, balance.getCasualLeaveUsed() - requestedDays));
                }
                leaveBalanceRepository.save(balance);
            }
            log.info("Refunded {} days of {} leave to employeeId={} due to cancellation/rejection of request ID={}",
                    requestedDays, reqType, request.getEmployeeId(), request.getId());
        }
    }

    @Transactional
    public void clearEmployeeLeaveData(Long employeeId) {
        List<LeaveRequest> empRequests = leaveRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId);
        leaveRequestRepository.deleteAllInBatch(empRequests);

        List<LeaveBalance> balances = leaveBalanceRepository.findByEmployeeId(employeeId);
        for (LeaveBalance b : balances) {
            b.setCasualLeaveUsed(0.0);
            b.setSickLeaveUsed(0.0);
            b.setEarnedLeaveUsed(0.0);
            b.setWorkFromHomeUsed(0.0);
            b.setRestrictedHolidayUsed(0.0);
            b.setCarriedForwardLeaveQuota(0.0);
            b.setCarriedForwardLeaveUsed(0.0);
            b.setCarriedForwardExpired(false);
        }
        leaveBalanceRepository.saveAll(balances);

        List<EmployeeLeaveQuota> quotas = employeeLeaveQuotaRepository.findByEmployeeId(employeeId);
        for (EmployeeLeaveQuota q : quotas) {
            q.setUsed(0.0);
        }
        employeeLeaveQuotaRepository.saveAll(quotas);

        List<Attendance> attendances = attendanceRepository.findByEmployeeId(employeeId);
        List<Attendance> leaveAttendances = attendances.stream()
                .filter(a -> "ON_LEAVE".equalsIgnoreCase(a.getStatus()) || "HALF_DAY_LEAVE".equalsIgnoreCase(a.getStatus()))
                .toList();
        attendanceRepository.deleteAllInBatch(leaveAttendances);
        log.info("Cleared leave requests and reset balances for employeeId={}", employeeId);
    }
}
