package com.example.hr_management_backend.features.leaves.service;

import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import com.example.hr_management_backend.features.leaves.event.LeaveApprovedEvent;
import com.example.hr_management_backend.features.leaves.event.LeaveWithdrawnEvent;
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
    private String capturePolicySnapshot() {
        try {
            String mode = settingsService.getSetting("time_off_policy_mode");
            String cycle = settingsService.getSetting("time_off_cycle");
            String sbUnit = settingsService.getSetting("time_off_short_break_unit_limit");
            String eoUnit = settingsService.getSetting("time_off_early_out_unit_limit");
            String hourly = settingsService.getSetting("time_off_hourly_limit");
            
            return "{"
                    + "\"time_off_policy_mode\":\"" + (mode != null ? mode : "") + "\","
                    + "\"time_off_cycle\":\"" + (cycle != null ? cycle : "") + "\","
                    + "\"time_off_short_break_unit_limit\":\"" + (sbUnit != null ? sbUnit : "") + "\","
                    + "\"time_off_early_out_unit_limit\":\"" + (eoUnit != null ? eoUnit : "") + "\","
                    + "\"time_off_hourly_limit\":\"" + (hourly != null ? hourly : "") + "\""
                    + "}";
        } catch (Exception e) {
            log.error("Failed to capture policy snapshot", e);
            return "{}";
        }
    }

    /**
     * Fetches or creates a leave balance for the given employee and year.
     * Computes real-time pending leave requests to ensure `getCasualLeaveRemaining()`
     * dynamically decrements immediately when a leave application is submitted.
     */
    @Transactional
    public LeaveBalance getOrCreateLeaveBalance(Long employeeId, Integer year) {
        LeaveBalance balance = leaveBalanceRepository.findByEmployeeIdAndYear(employeeId, year)
                .orElseGet(() -> {
                    LeaveBalance initial = LeaveBalance.builder()
                            .employeeId(employeeId)
                            .year(year)
                            .casualLeaveQuota(12.0)
                            .casualLeaveUsed(0.0)
                            .sickLeaveQuota(10.0)
                            .sickLeaveUsed(0.0)
                            .earnedLeaveQuota(15.0)
                            .earnedLeaveUsed(0.0)
                            .build();
                    return leaveBalanceRepository.save(initial);
                });

        Double pendingCasual = leaveRequestRepository.sumPendingLeaves(employeeId, "CASUAL", year);
        Double pendingSick = leaveRequestRepository.sumPendingLeaves(employeeId, "SICK", year);
        Double pendingEarned = leaveRequestRepository.sumPendingLeaves(employeeId, "EARNED", year);
        Double pendingWfh = leaveRequestRepository.sumPendingLeaves(employeeId, "WORK_FROM_HOME", year);

        balance.setCasualLeavePending(pendingCasual != null ? pendingCasual : 0.0);
        balance.setSickLeavePending(pendingSick != null ? pendingSick : 0.0);
        balance.setEarnedLeavePending(pendingEarned != null ? pendingEarned : 0.0);
        balance.setWorkFromHomePending(pendingWfh != null ? pendingWfh : 0.0);

        return balance;
    }

    @Transactional
    @CacheEvict(value = {"employees", "employee_details", "leave_balances"}, allEntries = true)
    public LeaveRequest applyForLeave(LeaveRequest request) {
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

        // Validate session overlaps
        validateSessionOverlap(request, isTimeBased);

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

        // Server-side authoritative calculation of totalDays — never trust client value alone
        double daysToApply = isTimeBased ? 0.0 : computeTotalDays(
                request.getStartDate(), request.getEndDate(),
                request.getStartSession(), request.getEndSession());
        request.setTotalDays(daysToApply);

        if (request.getLeaveType() == null || request.getLeaveType().isBlank()) {
            request.setLeaveType("CASUAL");
        }

        // Capture policy snapshot for ALL leave types (audit trail)
        request.setPolicySnapshot(capturePolicySnapshot());

        // Validate balance for non-time-based leave types
        if (!isTimeBased) {
            int currentYear = request.getStartDate().getYear();
            String reqType = request.getLeaveType().toUpperCase();
            double remaining = 0.0;

            if ("UNPAID".equals(reqType) || "WORK_FROM_HOME".equals(reqType)) {
                remaining = 999.0;
            } else {
                // Check if special leave type exists in dynamic EmployeeLeaveQuota table
                var specialQuota = employeeLeaveQuotaRepository.findByEmployeeIdAndYearAndLeaveType(request.getEmployeeId(), currentYear, reqType);
                if (specialQuota.isPresent()) {
                    remaining = specialQuota.get().getRemaining();
                } else {
                    LeaveBalance balance = getOrCreateLeaveBalance(request.getEmployeeId(), currentYear);
                    String normType = reqType.replaceAll("_LEAVE$", "");
                    remaining = switch (normType) {
                        case "SICK" -> balance.getSickLeaveRemaining();
                        case "EARNED" -> balance.getEarnedLeaveRemaining();
                        case "WORK_FROM_HOME", "WFH" -> balance.getWorkFromHomeRemaining();
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
            // Enforce admin-configured time-off policies for short breaks and early outs
            validateTimeBasedPolicy(request, type);
        }

        return leaveRequestRepository.save(request);
    }


    /**
     * Approves or rejects a leave request.
     * Uses Pessimistic DB Locking on LeaveBalance to prevent race conditions during deduction.
     */
    @Transactional
    @CacheEvict(value = {"employees", "employee_details", "leave_balances"}, allEntries = true)
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
            double requestedDays = request.getTotalDays();
            String reqType = request.getLeaveType().toUpperCase();

            var specialQuotaOpt = employeeLeaveQuotaRepository.findByEmployeeIdAndYearAndLeaveType(request.getEmployeeId(), year, reqType);

            if (specialQuotaOpt.isPresent()) {
                EmployeeLeaveQuota quota = specialQuotaOpt.get();
                if (quota.getRemaining() < requestedDays) {
                    throw new IllegalStateException("Cannot approve: Insufficient " + reqType + " leave balance.");
                }
                quota.setUsed(quota.getUsed() + requestedDays);
                employeeLeaveQuotaRepository.save(quota);
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
                    case "EARNED" -> {
                        if (balance.getEarnedLeaveRemaining() < requestedDays) {
                            throw new IllegalStateException("Cannot approve: Insufficient earned leave balance.");
                        }
                        balance.setEarnedLeaveUsed(balance.getEarnedLeaveUsed() + requestedDays);
                    }
                    case "WORK_FROM_HOME", "WFH" -> {
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
        }

        LeaveRequest saved = leaveRequestRepository.save(request);

        if ("APPROVED".equalsIgnoreCase(saved.getStatus())) {
            eventPublisher.publishEvent(new LeaveApprovedEvent(
                    saved.getEmployeeId(),
                    saved.getStartDate(),
                    saved.getEndDate(),
                    saved.getLeaveType()
            ));
        }

        return saved;
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
    @CacheEvict(value = {"employees", "employee_details", "leave_balances"}, allEntries = true)
    public void bulkGrantLeaves(String leaveType, int grantDays, List<Long> excludedEmployeeIds) {
        int currentYear = LocalDate.now().getYear();
        List<Employee> employees = employeeRepository.findAll();
        List<Long> exclusions = (excludedEmployeeIds != null) ? excludedEmployeeIds : List.of();

        List<LeaveBalance> balancesToSave = new ArrayList<>();
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
                case "EARNED" -> {
                    if (balance.getEarnedLeaveRemaining() < daysToApply) {
                        throw new IllegalStateException("Cannot apply: Insufficient earned leave balance for employee.");
                    }
                    balance.setEarnedLeaveUsed(balance.getEarnedLeaveUsed() + daysToApply);
                }
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
     * Withdraws an already-APPROVED leave and credits back the balance.
     * Only HR/SUPER_ADMIN can call this.
     */
    @Transactional
    @CacheEvict(value = {"employees", "employee_details", "leave_balances"}, allEntries = true)
    public LeaveRequest withdrawApprovedLeave(Long requestId, Long actorId) {
        LeaveRequest request = leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Leave request not found: " + requestId));

        if (!"APPROVED".equalsIgnoreCase(request.getStatus())) {
            throw new IllegalStateException("Only APPROVED leaves can be withdrawn. Current status: " + request.getStatus());
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
                    case "EARNED" -> balance.setEarnedLeaveUsed(Math.max(0, balance.getEarnedLeaveUsed() - daysToCredit));
                    case "WORK_FROM_HOME", "WFH" -> balance.setWorkFromHomeUsed(Math.max(0, balance.getWorkFromHomeUsed() - daysToCredit));
                    case "UNPAID" -> log.info("Unpaid leave withdrawn — no balance change for employeeId={}", request.getEmployeeId());
                    default -> balance.setCasualLeaveUsed(Math.max(0, balance.getCasualLeaveUsed() - daysToCredit));
                }
                leaveBalanceRepository.save(balance);
            }
            log.info("Balance credited back: {} days of {} for employeeId={}", daysToCredit, type, request.getEmployeeId());
        }

        request.setStatus("WITHDRAWN");
        request.setRejectionReason("Withdrawn by HR/Admin (actorId=" + actorId + ")");
        LeaveRequest saved = leaveRequestRepository.save(request);

        eventPublisher.publishEvent(new LeaveWithdrawnEvent(
                saved.getEmployeeId(),
                saved.getStartDate(),
                saved.getEndDate(),
                saved.getLeaveType()
        ));

        return saved;
    }


    @Transactional
    @CacheEvict(value = {"employees", "employee_details"}, allEntries = true)
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
            // Hour-based: for now count requests as a proxy (each short break ~ 1 unit of quota).
            // When hour tracking is added, this can sum actual durations.
            int hourlyLimit = parseIntSetting("time_off_hourly_limit", 4);

            if ("HOURLY_COMBINED".equalsIgnoreCase(policyMode)) {
                // Combined: total of short breaks + early outs + late arrivals must not exceed the limit
                long shortCount = leaveRequestRepository.countTimeBasedRequestsInCycle(
                        request.getEmployeeId(), cycleStart, cycleEnd, "%SHORT%");
                long earlyCount = leaveRequestRepository.countTimeBasedRequestsInCycle(
                        request.getEmployeeId(), cycleStart, cycleEnd, "%EARLY%");
                long lateCount = leaveRequestRepository.countTimeBasedRequestsInCycle(
                        request.getEmployeeId(), cycleStart, cycleEnd, "%LATE%");
                long totalCount = shortCount + earlyCount + lateCount;
                if (totalCount >= hourlyLimit) {
                    throw new IllegalStateException("Combined time-off limit reached (" + hourlyLimit + " per " + cycle.toLowerCase() + "). Used: " + totalCount);
                }
            } else {
                // Separate: each type has its own hourly limit
                if (isShortBreak) {
                    long currentCount = leaveRequestRepository.countTimeBasedRequestsInCycle(
                            request.getEmployeeId(), cycleStart, cycleEnd, "%SHORT%");
                    if (currentCount >= hourlyLimit) {
                        throw new IllegalStateException("Short break hourly limit reached (" + hourlyLimit + " per " + cycle.toLowerCase() + "). Used: " + currentCount);
                    }
                } else if (isEarlyOut) {
                    long currentCount = leaveRequestRepository.countTimeBasedRequestsInCycle(
                            request.getEmployeeId(), cycleStart, cycleEnd, "%EARLY%");
                    if (currentCount >= hourlyLimit) {
                        throw new IllegalStateException("Early out hourly limit reached (" + hourlyLimit + " per " + cycle.toLowerCase() + "). Used: " + currentCount);
                    }
                }
            }
        }

        log.info("Time-off policy validated: mode={}, cycle={}, type={}, employeeId={}",
                policyMode, cycle, normalizedType, request.getEmployeeId());
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

                    if (date.isAfter(existing.getStartDate()) && date.isBefore(existing.getEndDate())) {
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
    public void clearEmployeeLeaveData(Long employeeId) {
        List<LeaveRequest> empRequests = leaveRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId);
        leaveRequestRepository.deleteAllInBatch(empRequests);

        List<LeaveBalance> balances = leaveBalanceRepository.findByEmployeeId(employeeId);
        for (LeaveBalance b : balances) {
            b.setCasualLeaveUsed(0.0);
            b.setSickLeaveUsed(0.0);
            b.setEarnedLeaveUsed(0.0);
            b.setWorkFromHomeUsed(0.0);
        }
        leaveBalanceRepository.saveAll(balances);

        List<EmployeeLeaveQuota> quotas = employeeLeaveQuotaRepository.findByEmployeeIdAndYear(employeeId, java.time.Year.now().getValue());
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
