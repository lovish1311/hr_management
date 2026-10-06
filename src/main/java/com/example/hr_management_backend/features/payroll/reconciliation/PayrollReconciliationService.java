package com.example.hr_management_backend.features.payroll.reconciliation;

import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import com.example.hr_management_backend.features.payroll.model.PayrollRecord;
import com.example.hr_management_backend.features.payroll.repository.PayrollRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayrollReconciliationService {

    private final PayrollRecordRepository payrollRecordRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional(readOnly = true)
    public PayrollReconciliationReportDto generateReconciliationReport(String monthStr, Integer year) {
        String currentMonth = normalizeMonth(monthStr);
        int currentYear = (year != null && year > 0) ? year : LocalDate.now().getYear();

        YearMonth curYm = YearMonth.of(currentYear, parseMonthNumber(currentMonth));
        YearMonth prevYm = curYm.minusMonths(1);
        String previousMonth = prevYm.getMonth().name();
        int previousYear = prevYm.getYear();

        List<PayrollRecord> currentRecords = payrollRecordRepository
                .findByPayrollMonthIgnoreCaseAndPayrollYear(currentMonth, currentYear);
        List<PayrollRecord> previousRecords = payrollRecordRepository
                .findByPayrollMonthIgnoreCaseAndPayrollYear(previousMonth, previousYear);

        Map<Long, PayrollRecord> curMap = currentRecords.stream()
                .collect(Collectors.toMap(PayrollRecord::getEmployeeId, Function.identity(), (a, b) -> a));
        Map<Long, PayrollRecord> prevMap = previousRecords.stream()
                .collect(Collectors.toMap(PayrollRecord::getEmployeeId, Function.identity(), (a, b) -> a));

        Set<Long> allEmpIds = new LinkedHashSet<>();
        allEmpIds.addAll(curMap.keySet());
        allEmpIds.addAll(prevMap.keySet());

        List<EmployeePayrollVarianceDto> variances = new ArrayList<>();

        BigDecimal curGrossSum = BigDecimal.ZERO;
        BigDecimal prevGrossSum = BigDecimal.ZERO;
        BigDecimal curNetSum = BigDecimal.ZERO;
        BigDecimal prevNetSum = BigDecimal.ZERO;

        BigDecimal pfSum = BigDecimal.ZERO;
        BigDecimal esiSum = BigDecimal.ZERO;
        BigDecimal ptSum = BigDecimal.ZERO;
        BigDecimal tdsSum = BigDecimal.ZERO;
        BigDecimal arrearsSum = BigDecimal.ZERO;

        for (Long empId : allEmpIds) {
            PayrollRecord cur = curMap.get(empId);
            PayrollRecord prev = prevMap.get(empId);

            Employee emp = employeeRepository.findById(empId).orElse(null);
            String name = cur != null ? cur.getEmployeeName() : (prev != null ? prev.getEmployeeName() : "Employee #" + empId);
            String code = cur != null ? cur.getEmployeeCode() : (prev != null ? prev.getEmployeeCode() : "");
            String dept = cur != null ? cur.getDepartment() : (prev != null ? prev.getDepartment() : "");

            BigDecimal cGross = cur != null && cur.getTotalGrossPay() != null ? cur.getTotalGrossPay() : BigDecimal.ZERO;
            BigDecimal pGross = prev != null && prev.getTotalGrossPay() != null ? prev.getTotalGrossPay() : BigDecimal.ZERO;
            BigDecimal cNet = cur != null && cur.getNetPay() != null ? cur.getNetPay() : BigDecimal.ZERO;
            BigDecimal pNet = prev != null && prev.getNetPay() != null ? prev.getNetPay() : BigDecimal.ZERO;

            Double cLop = cur != null && cur.getLopDays() != null ? cur.getLopDays() : 0.0;
            Double pLop = prev != null && prev.getLopDays() != null ? prev.getLopDays() : 0.0;

            BigDecimal arrears = cur != null && cur.getAdHocBonus() != null ? cur.getAdHocBonus() : BigDecimal.ZERO; // or arrears column
            BigDecimal bonus = cur != null && cur.getAdHocBonus() != null ? cur.getAdHocBonus() : BigDecimal.ZERO;

            curGrossSum = curGrossSum.add(cGross);
            prevGrossSum = prevGrossSum.add(pGross);
            curNetSum = curNetSum.add(cNet);
            prevNetSum = prevNetSum.add(pNet);

            if (cur != null) {
                if (cur.getCalculatedPf() != null) pfSum = pfSum.add(cur.getCalculatedPf());
                if (cur.getCalculatedEsi() != null) esiSum = esiSum.add(cur.getCalculatedEsi());
                if (cur.getCalculatedPt() != null) ptSum = ptSum.add(cur.getCalculatedPt());
            }

            VarianceTag tag;
            String reason;

            BigDecimal grossDiff = cGross.subtract(pGross);
            BigDecimal netDiff = cNet.subtract(pNet);

            if (cur != null && prev == null) {
                tag = VarianceTag.NEW_JOINER;
                reason = "New joinee in " + currentMonth + " " + currentYear;
            } else if (cur == null && prev != null) {
                tag = VarianceTag.EXIT_SEPARATED;
                reason = "Exited / not on payroll for " + currentMonth + " " + currentYear;
            } else if (grossDiff.compareTo(BigDecimal.valueOf(10)) > 0) {
                tag = VarianceTag.SALARY_INCREASE;
                if (cur.getAdHocBonus() != null && cur.getAdHocBonus().compareTo(BigDecimal.ZERO) > 0) {
                    reason = "Earnings increased (+INR " + grossDiff.setScale(0, RoundingMode.HALF_UP) + ") with bonus of INR " + cur.getAdHocBonus().setScale(0, RoundingMode.HALF_UP);
                } else {
                    reason = "Salary increase / lower deductions vs previous month (+INR " + grossDiff.setScale(0, RoundingMode.HALF_UP) + ")";
                }
            } else if (grossDiff.compareTo(BigDecimal.valueOf(-10)) < 0) {
                tag = VarianceTag.SALARY_DECREASE_LOP;
                if (cLop > pLop) {
                    reason = "Reduced pay due to " + cLop + " LOP days (-INR " + grossDiff.abs().setScale(0, RoundingMode.HALF_UP) + ")";
                } else {
                    reason = "Gross earnings decrease (-INR " + grossDiff.abs().setScale(0, RoundingMode.HALF_UP) + ")";
                }
            } else {
                tag = VarianceTag.UNCHANGED;
                reason = "Standard consistent payroll";
            }

            variances.add(EmployeePayrollVarianceDto.builder()
                    .employeeId(empId)
                    .employeeName(name)
                    .employeeCode(code)
                    .department(dept)
                    .previousGrossPay(pGross)
                    .currentGrossPay(cGross)
                    .grossDifference(grossDiff)
                    .previousNetPay(pNet)
                    .currentNetPay(cNet)
                    .netDifference(netDiff)
                    .previousLopDays(pLop)
                    .currentLopDays(cLop)
                    .arrearsAmount(BigDecimal.ZERO)
                    .adHocBonus(bonus)
                    .calculatedTds(BigDecimal.ZERO)
                    .varianceTag(tag)
                    .varianceReason(reason)
                    .build());
        }

        BigDecimal grossDelta = curGrossSum.subtract(prevGrossSum);
        BigDecimal netDelta = curNetSum.subtract(prevNetSum);

        Double grossPercent = prevGrossSum.compareTo(BigDecimal.ZERO) > 0
                ? grossDelta.divide(prevGrossSum, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;

        Double netPercent = prevNetSum.compareTo(BigDecimal.ZERO) > 0
                ? netDelta.divide(prevNetSum, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;

        return PayrollReconciliationReportDto.builder()
                .currentMonth(currentMonth)
                .currentYear(currentYear)
                .previousMonth(previousMonth)
                .previousYear(previousYear)
                .currentHeadcount(currentRecords.size())
                .previousHeadcount(previousRecords.size())
                .headcountDelta(currentRecords.size() - previousRecords.size())
                .currentGrossTotal(curGrossSum)
                .previousGrossTotal(prevGrossSum)
                .grossTotalDelta(grossDelta)
                .grossPercentageDelta(grossPercent)
                .currentNetTotal(curNetSum)
                .previousNetTotal(prevNetSum)
                .netTotalDelta(netDelta)
                .netPercentageDelta(netPercent)
                .currentPfTotal(pfSum)
                .currentEsiTotal(esiSum)
                .currentPtTotal(ptSum)
                .currentTdsTotal(tdsSum)
                .currentArrearsTotal(arrearsSum)
                .employeeVariances(variances)
                .build();
    }

    private String normalizeMonth(String monthStr) {
        if (monthStr == null || monthStr.isBlank()) {
            return LocalDate.now().getMonth().name();
        }
        String clean = monthStr.trim().toUpperCase();
        try {
            int num = Integer.parseInt(clean);
            return Month.of(num).name();
        } catch (NumberFormatException e) {
            return clean;
        }
    }

    private int parseMonthNumber(String monthStr) {
        try {
            return Integer.parseInt(monthStr);
        } catch (Exception ignored) {}
        try {
            return Month.valueOf(monthStr.toUpperCase()).getValue();
        } catch (Exception e) {
            return LocalDate.now().getMonthValue();
        }
    }
}
