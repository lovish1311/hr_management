package com.example.hr_management_backend.features.leaves.policy.rules;

import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.leaves.model.LeaveBalance;
import com.example.hr_management_backend.features.leaves.model.LeaveRequest;
import com.example.hr_management_backend.features.leaves.policy.LeavePolicyRule;
import com.example.hr_management_backend.features.leaves.repository.EmployeeLeaveQuotaRepository;
import com.example.hr_management_backend.features.leaves.repository.LeaveBalanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class QuotaValidationRule implements LeavePolicyRule {

    private final EmployeeLeaveQuotaRepository employeeLeaveQuotaRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;

    @Override
    public String getRuleName() {
        return "QuotaValidationRule";
    }

    @Override
    public int getOrder() {
        return 30;
    }

    @Override
    public void validate(LeaveRequest request, Employee employee) {
        String reqType = request.getLeaveType() != null ? request.getLeaveType().toUpperCase() : "CASUAL";
        boolean isTimeBased = Boolean.TRUE.equals(request.getIsTimeBased())
                || reqType.contains("SHORT") || reqType.contains("EARLY") || reqType.contains("LATE");

        if (isTimeBased) {
            return;
        }

        int year = request.getStartDate().getYear();
        double requestedDays = request.getTotalDays() != null ? request.getTotalDays() : 1.0;
        double remaining = 0.0;

        if ("UNPAID".equals(reqType) || "WORK_FROM_HOME".equals(reqType) || "WFH".equals(reqType)) {
            return;
        }

        var specialQuota = employeeLeaveQuotaRepository.findByEmployeeIdAndYearAndLeaveType(request.getEmployeeId(), year, reqType);
        if (specialQuota.isPresent()) {
            remaining = specialQuota.get().getRemaining();
        } else {
            LeaveBalance balance = leaveBalanceRepository.findByEmployeeIdAndYear(request.getEmployeeId(), year)
                    .orElseGet(() -> LeaveBalance.builder()
                            .employeeId(request.getEmployeeId())
                            .year(year)
                            .casualLeaveQuota(12.0)
                            .casualLeaveUsed(0.0)
                            .sickLeaveQuota(12.0)
                            .sickLeaveUsed(0.0)
                            .earnedLeaveQuota(15.0)
                            .earnedLeaveUsed(0.0)
                            .workFromHomeQuota(24.0)
                            .workFromHomeUsed(0.0)
                            .build());

            String normType = reqType.replaceAll("_LEAVE$", "");
            remaining = switch (normType) {
                case "SICK" -> balance.getSickLeaveRemaining();
                case "EARNED" -> balance.getEarnedLeaveRemaining();
                default -> balance.getCasualLeaveRemaining();
            };
        }

        if (requestedDays > remaining) {
            throw new IllegalStateException("Insufficient " + reqType + " leave balance. Available: " + Math.max(0.0, remaining) + " days.");
        }
    }
}
