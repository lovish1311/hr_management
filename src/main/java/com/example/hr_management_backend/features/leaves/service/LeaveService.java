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

                    return leaveBalanceRepository.save(initial);
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
    @CacheEvict(value = {"employees", "employee_details", "leave_balances"}, allEntries = true)
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
    @CacheEvict(value = {"employees", "employee_details", "leave_balances"}, allEntries = true)
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

        String type = request.getLeaveType() != null ? request.getLeaveType().toUpperCase().replaceAll("[ -]", "_") : "";
        boolean isTimeBased = type.contains("SHORT_BREAK") || type.contains("SHORT_LEAVE")
                || type.contains("EARLY_OUT") || type.contains("EARLY_LEAVE") || type.contains("LATE_ARRIVAL");
        request.setIsTimeBased(isTimeBased);

        // Validate session overlaps
        validateSessionOverlap(request, isTimeBased);

        // Dynamically resolve assigned manager for leave routing
        if (employee.getManager() != null) {
            request.setApprovedBy(employee.getManager().getId());
            log.info("Leave request routed dynamically to assigned manager: {} (ID: {})",
                    employee.getManager().getFirstName() + " " + employee.getManager().getLastName(), employee.getManager().getId());
        }

        // Server-side authoritative calculation of totalDays — never trust client value alone
        double daysToApply = isTimeBased ? 0.0 : computeTotalDays(
                request.getStartDate(), request.getEndDate(),
                request.getStartSession(), request.getEndSession());
        request.setTotalDays(daysToApply);

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
                remaining = 999.0;
            } else {
                // Check if special leave type exists in dynamic EmployeeLeaveQuota table
                var specialQuota = employeeLeaveQuotaRepository.findByEmployeeIdAndYearAndLeaveType(request.getEmployeeId(), currentYear, reqType);
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
    @CacheEvict(value = {"employees", "employee_details", "leave_balances"}, allEntries = true)
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
    @CacheEvict(value = {"employees", "employee_details", "leave_balances"}, allEntries = true)
    public LeaveRequest updateStatus(Long requestId, String status, String rejectionReason, String actorEmail) {
        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Leave request not found with id: " + requestId));

        String previousStatus = request.getStatus() != null ? request.getStatus().toUpperCase() : "PENDING";
        String newStatus = status.toUpperCase();

        if (previousStatus.equals(newStatus)) {
            return request;
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

        int year = request.getStartDate().getYear();
        double requestedDays = request.getTotalDays() != null ? request.getTotalDays() : 0.0;
        String reqType = request.getLeaveType() != null ? request.getLeaveType().toUpperCase() : "CASUAL";
        boolean isTimeBased = reqType.contains("SHORT") || reqType.contains("EARLY") || reqType.contains("LATE");

        if ("APPROVED".equalsIgnoreCase(newStatus)) {
            var specialQuotaOpt = employeeLeaveQuotaRepository.findByEmployeeIdAndYearAndLeaveType(request.getEmployeeId(), year, reqType);

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
        } else if ("REJECTED".equalsIgnoreCase(newStatus) && "APPROVED".equalsIgnoreCase(previousStatus)) {
            // Revert/refund used balance because a previously approved leave is now rejected
            if (!isTimeBased) {
                var specialQuotaOpt = employeeLeaveQuotaRepository.findByEmployeeIdAndYearAndLeaveType(request.getEmployeeId(), year, reqType);
                if (specialQuotaOpt.isPresent()) {
                    EmployeeLeaveQuota quota = specialQuotaOpt.get();
                    quota.setUsed(Math.max(0.0, quota.getUsed() - requestedDays));
                    employeeLeaveQuotaRepository.save(quota);
                } else if (isDynamicQuotaType(reqType)) {
                    log.info("No special quota record found for refund of dynamic leave {}", reqType);
                } else {
                    LeaveBalance balance = leaveBalanceRepository.findByEmployeeIdAndYearForUpdate(request.getEmployeeId(), year)
                            .orElseGet(() -> getOrCreateLeaveBalance(request.getEmployeeId(), year));

                    String normType = reqType.replaceAll("_LEAVE$", "");
                    switch (normType) {
                        case "SICK" -> balance.setSickLeaveUsed(Math.max(0.0, balance.getSickLeaveUsed() - requestedDays));
                        case "EARNED" -> refundEarnedLeaveWithPriority(balance, requestedDays);
                        case "WORK_FROM_HOME", "WFH" -> balance.setWorkFromHomeUsed(Math.max(0.0, balance.getWorkFromHomeUsed() - requestedDays));
                        case "UNPAID" -> log.info("Unpaid leave rejected — no balance change for employeeId={}", request.getEmployeeId());
                        case "RESTRICTED_HOLIDAY", "RESTRICTED" -> balance.setRestrictedHolidayUsed(Math.max(0.0, balance.getRestrictedHolidayUsed() - requestedDays));
                        default -> balance.setCasualLeaveUsed(Math.max(0.0, balance.getCasualLeaveUsed() - requestedDays));
                    }
                    leaveBalanceRepository.save(balance);
                }
                log.info("Refunded {} days of {} leave to employeeId={} due to rejection of approved leave.", requestedDays, reqType, request.getEmployeeId());
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
        } else if ("REJECTED".equalsIgnoreCase(saved.getStatus()) && "APPROVED".equalsIgnoreCase(previousStatus)) {
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
        List<LeaveRequest> requests = leaveRequestRepository.findByStatusOrderByCreatedAtDesc("PENDING");
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
    @CacheEvict(value = {"employees", "employee_details", "leave_balances"}, allEntries = true)
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
    @CacheEvict(value = {"employees", "employee_details", "leave_balances"}, allEntries = true)
    public LeaveRequest applyOnBehalfByHr(LeaveRequest request) {
        request.setId(null);
        request.setStatus("APPROVED");
        request.setRejectionReason("Applied directly by HR Admin");

        if (request.getStartDate() == null || request.getEndDate() == null) {
            request.setStartDate(LocalDate.now());
            request.setEndDate(LocalDate.now());
        }

        String onBehalfType = request.getLeaveType() != null ? request.getLeaveType().toUpperCase().replaceAll("[ -]", "_") : "";
        boolean isTimeBased = onBehalfType.contains("SHORT_BREAK") || onBehalfType.contains("SHORT_LEAVE")
                || onBehalfType.contains("EARLY_OUT") || onBehalfType.contains("EARLY_LEAVE") || onBehalfType.contains("LATE_ARRIVAL");
        request.setIsTimeBased(isTimeBased);

        // Validate session overlaps
        validateSessionOverlap(request, isTimeBased);

        // Server-side authoritative totalDays calculation
        double daysToApply = isTimeBased ? 0.0 : computeTotalDays(
                request.getStartDate(), request.getEndDate(),
                request.getStartSession(), request.getEndSession());
        request.setTotalDays(daysToApply);

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
            String normType = request.getLeaveType().toUpperCase().replaceAll("_LEAVE$", "");
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
                default -> {
                    if (balance.getCasualLeaveRemaining() < daysToApply) {
                        throw new IllegalStateException("Cannot apply: Insufficient casual leave balance for employee.");
                    }
                    balance.setCasualLeaveUsed(balance.getCasualLeaveUsed() + daysToApply);
                }
            }
            leaveBalanceRepository.save(balance);
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
    @CacheEvict(value = {"employees", "employee_details", "leave_balances"}, allEntries = true)
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

                var specialQuotaOpt = employeeLeaveQuotaRepository.findByEmployeeIdAndYearAndLeaveType(request.getEmployeeId(), year, type);
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
    @CacheEvict(value = {"employees", "employee_details", "leave_balances"}, allEntries = true)
    public LeaveBalance adjustEmployeeBalance(Long employeeId, String leaveType, int adjustmentDays) {
        int year = LocalDate.now().getYear();
        String upperType = leaveType.toUpperCase();

        if (isDynamicQuotaType(upperType)) {
            EmployeeLeaveQuota quota = employeeLeaveQuotaRepository
                    .findByEmployeeIdAndYearAndLeaveType(employeeId, year, upperType)
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

    public boolean isDynamicQuotaType(String type) {
        if (type == null) return false;
        String upper = type.toUpperCase().replaceAll("_LEAVE$", "");
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

            double incomingHours = 1.0;
            if (request.getStartTime() != null && request.getEndTime() != null) {
                long mins = ChronoUnit.MINUTES.between(request.getStartTime(), request.getEndTime());
                if (mins > 0) incomingHours = mins / 60.0;
            }

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

    private double computeTotalTimeBasedHours(Long employeeId, LocalDate cycleStart, LocalDate cycleEnd, String typePattern) {
        List<LeaveRequest> requests = leaveRequestRepository.findTimeBasedRequestsInCycle(employeeId, cycleStart, cycleEnd, typePattern);
        double totalHours = 0.0;
        for (LeaveRequest req : requests) {
            if (req.getStartTime() != null && req.getEndTime() != null) {
                long mins = ChronoUnit.MINUTES.between(req.getStartTime(), req.getEndTime());
                totalHours += (mins > 0 ? mins / 60.0 : 1.0);
            } else {
                totalHours += 1.0;
            }
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
        if (start == null || end == null) return 1.0;

        boolean startsS2 = isSession2(startSession) && !isSession1(startSession);
        boolean endsS1 = isSession1(endSession) && !isSession2(endSession);

        if (start.equals(end)) {
            // Same day
            if (startsS2 || endsS1) {
                // Either half of the same day
                if (startsS2 && endsS1) return 0.0; // SESSION_2 start on same day as SESSION_1 end: invalid, treat as 0
                return 0.5;
            }
            return 1.0; // Both sessions or unspecified = full day
        }

        long rawDays = ChronoUnit.DAYS.between(start, end) + 1;
        double adjustment = 0.0;
        if (startsS2) adjustment -= 0.5;  // starts at afternoon, loses half a day
        if (endsS1) adjustment -= 0.5;    // ends at morning, loses half a day
        return Math.max(0.5, rawDays + adjustment);
    }

    private void validateSessionOverlap(LeaveRequest newReq, boolean isTimeBased) {
        List<LeaveRequest> existingLeaves = leaveRequestRepository.findOverlappingLeaves(
                newReq.getEmployeeId(), newReq.getStartDate(), newReq.getEndDate());

        if (existingLeaves.isEmpty()) {
            return;
        }

        for (LocalDate date = newReq.getStartDate(); !date.isAfter(newReq.getEndDate()); date = date.plusDays(1)) {
            boolean newS1 = false;
            boolean newS2 = false;

            if (isTimeBased) {
                if (newReq.getStartTime() != null) {
                    if (newReq.getStartTime().isBefore(LocalTime.of(13, 30))) {
                        newS1 = true;
                    } else {
                        newS2 = true;
                    }
                } else {
                    newS1 = true;
                    newS2 = true;
                }
            } else {
                if (date.isAfter(newReq.getStartDate()) && date.isBefore(newReq.getEndDate())) {
                    // Middle days: always full day
                    newS1 = true;
                    newS2 = true;
                } else if (newReq.getStartDate().equals(newReq.getEndDate())) {
                    // Same-day: use normalized session helpers
                    boolean hasS1 = isSession1(newReq.getStartSession());
                    boolean hasS2 = isSession2(newReq.getEndSession());
                    newS1 = hasS1 || (!hasS1 && !hasS2); // default to full day if no session specified
                    newS2 = hasS2 || (!hasS1 && !hasS2);
                } else if (date.equals(newReq.getStartDate())) {
                    newS1 = !isSession2(newReq.getStartSession()); // S1 unless explicitly starting S2
                    newS2 = true;
                } else if (date.equals(newReq.getEndDate())) {
                    newS1 = true;
                    newS2 = !isSession1(newReq.getEndSession()); // S2 unless explicitly ending S1
                }
            }

            for (LeaveRequest existing : existingLeaves) {
                if (!date.isBefore(existing.getStartDate()) && !date.isAfter(existing.getEndDate())) {
                    boolean extS1 = false;
                    boolean extS2 = false;

                    if (Boolean.TRUE.equals(existing.getIsTimeBased())) {
                        if (existing.getStartTime() != null) {
                            if (existing.getStartTime().isBefore(LocalTime.of(13, 30))) {
                                extS1 = true;
                            } else {
                                extS2 = true;
                            }
                        } else {
                            extS1 = true;
                            extS2 = true;
                        }
                    } else if (date.isAfter(existing.getStartDate()) && date.isBefore(existing.getEndDate())) {
                        extS1 = true;
                        extS2 = true;
                    } else if (existing.getStartDate().equals(existing.getEndDate())) {
                        boolean hasS1 = isSession1(existing.getStartSession());
                        boolean hasS2 = isSession2(existing.getEndSession());
                        extS1 = hasS1 || (!hasS1 && !hasS2);
                        extS2 = hasS2 || (!hasS1 && !hasS2);
                    } else if (date.equals(existing.getStartDate())) {
                        extS1 = !isSession2(existing.getStartSession());
                        extS2 = true;
                    } else if (date.equals(existing.getEndDate())) {
                        extS1 = true;
                        extS2 = !isSession1(existing.getEndSession());
                    }

                    if ((newS1 && extS1) || (newS2 && extS2)) {
                        throw new IllegalStateException(
                            "Leave conflict on " + date + ": A request is already PENDING/APPROVED for the same session.");
                    }
                }
            }
        }
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
    @CacheEvict(value = {"employees", "employee_details", "leave_balances"}, allEntries = true)
    public LeaveRequest cancelLeaveRequest(Long requestId, Long actorId) {
        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Leave request not found with id: " + requestId));

        String oldStatus = request.getStatus() != null ? request.getStatus().toUpperCase() : "PENDING";
        if ("CANCELLED".equals(oldStatus) || "REJECTED".equals(oldStatus)) {
            throw new IllegalStateException("Leave request is already " + oldStatus);
        }

        // Refund used balance if the request was previously APPROVED
        if ("APPROVED".equals(oldStatus)) {
            int year = request.getStartDate().getYear();
            double requestedDays = request.getTotalDays() != null ? request.getTotalDays() : 0.0;
            String reqType = request.getLeaveType() != null ? request.getLeaveType().toUpperCase() : "CASUAL";
            boolean isTimeBased = Boolean.TRUE.equals(request.getIsTimeBased());

            if (!isTimeBased && requestedDays > 0) {
                var specialQuotaOpt = employeeLeaveQuotaRepository.findByEmployeeIdAndYearAndLeaveType(request.getEmployeeId(), year, reqType);
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
                log.info("Refunded {} days of {} leave to employeeId={} due to cancellation of approved request ID={}",
                        requestedDays, reqType, request.getEmployeeId(), requestId);
            }
        }

        request.setStatus("CANCELLED");
        LeaveRequest saved = leaveRequestRepository.save(request);

        leavePolicyEngine.handleStatusChange(saved, oldStatus, "CANCELLED");
        eventPublisher.publishEvent(new com.example.hr_management_backend.features.leaves.event.LeaveWithdrawnEvent(
                saved.getEmployeeId(), saved.getStartDate(), saved.getEndDate(), saved.getLeaveType()
        ));

        return saved;
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
