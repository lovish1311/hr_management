package com.example.hr_management_backend.features.payroll.arrears;

import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import com.example.hr_management_backend.features.payroll.model.PayrollRecord;
import com.example.hr_management_backend.features.payroll.model.PayrollStatus;
import com.example.hr_management_backend.features.payroll.model.SalaryStructure;
import com.example.hr_management_backend.features.payroll.repository.PayrollRecordRepository;
import com.example.hr_management_backend.features.payroll.repository.SalaryStructureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArrearsService {

    private final ArrearsRecordRepository arrearsRecordRepository;
    private final EmployeeRepository employeeRepository;
    private final SalaryStructureRepository salaryStructureRepository;
    private final PayrollRecordRepository payrollRecordRepository;

    @Transactional
    public ArrearsDto recordManualArrears(ArrearsDto dto) {
        if (dto.getEmployeeId() == null) {
            throw new IllegalArgumentException("Employee ID is required.");
        }
        if (dto.getArrearsAmount() == null || dto.getArrearsAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Arrears amount must be greater than zero.");
        }

        Employee emp = employeeRepository.findById(dto.getEmployeeId())
                .orElseThrow(() -> new IllegalArgumentException("Employee not found with ID: " + dto.getEmployeeId()));

        ArrearsRecord record = ArrearsRecord.builder()
                .employeeId(emp.getId())
                .targetMonth(dto.getTargetMonth() != null ? dto.getTargetMonth().toUpperCase() : LocalDate.now().getMonth().name())
                .targetYear(dto.getTargetYear() != null ? dto.getTargetYear() : LocalDate.now().getYear())
                .sourceMonth(dto.getSourceMonth() != null ? dto.getSourceMonth().toUpperCase() : null)
                .sourceYear(dto.getSourceYear())
                .arrearsAmount(dto.getArrearsAmount())
                .reason(dto.getReason() != null ? dto.getReason() : "Manual retroactive salary adjustment")
                .isProcessed(false)
                .build();

        ArrearsRecord saved = arrearsRecordRepository.save(record);
        log.info("Recorded arrears entry of INR {} for employee {} (Target: {} {})",
                saved.getArrearsAmount(), emp.getEmployeeCode(), saved.getTargetMonth(), saved.getTargetYear());

        return mapToDto(saved, emp);
    }

    @Transactional(readOnly = true)
    public List<ArrearsDto> getPendingArrearsForCycle(String targetMonth, Integer targetYear) {
        List<ArrearsRecord> list = arrearsRecordRepository
                .findByTargetMonthIgnoreCaseAndTargetYearAndIsProcessedFalse(targetMonth, targetYear);

        return list.stream().map(r -> {
            Employee emp = employeeRepository.findById(r.getEmployeeId()).orElse(null);
            return mapToDto(r, emp);
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BigDecimal calculatePendingArrearsTotalForEmployee(Long employeeId, String targetMonth, Integer targetYear) {
        List<ArrearsRecord> list = arrearsRecordRepository
                .findByTargetMonthIgnoreCaseAndTargetYearAndIsProcessedFalse(targetMonth, targetYear)
                .stream()
                .filter(r -> r.getEmployeeId().equals(employeeId))
                .collect(Collectors.toList());

        return list.stream()
                .map(ArrearsRecord::getArrearsAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    public void markArrearsAsProcessed(Long employeeId, String targetMonth, Integer targetYear, UUID payrollRecordId) {
        List<ArrearsRecord> list = arrearsRecordRepository
                .findByTargetMonthIgnoreCaseAndTargetYearAndIsProcessedFalse(targetMonth, targetYear)
                .stream()
                .filter(r -> r.getEmployeeId().equals(employeeId))
                .collect(Collectors.toList());

        for (ArrearsRecord r : list) {
            r.setIsProcessed(true);
            r.setProcessedRecordId(payrollRecordId);
            arrearsRecordRepository.save(r);
        }
    }

    /**
     * Auto-detects retroactive salary structure revisions:
     * Compares active structure effective date with published past payslips.
     */
    @Transactional
    public List<ArrearsDto> autoDetectAndQueueStructuralArrears(Long employeeId, String currentMonth, Integer currentYear) {
        Optional<SalaryStructure> optStruct = salaryStructureRepository.findByEmployeeIdAndIsActiveTrue(employeeId);
        if (optStruct.isEmpty() || optStruct.get().getEffectiveDate() == null) {
            return List.of();
        }

        SalaryStructure currentStruct = optStruct.get();
        LocalDate effDate = currentStruct.getEffectiveDate();
        LocalDate curFirstDay = LocalDate.of(currentYear, parseMonthNumber(currentMonth), 1);

        if (!effDate.isBefore(curFirstDay)) {
            return List.of(); // Not back-dated
        }

        Employee emp = employeeRepository.findById(employeeId).orElse(null);
        if (emp == null) return List.of();

        List<ArrearsDto> proposed = new ArrayList<>();
        YearMonth startYm = YearMonth.from(effDate);
        YearMonth endYm = YearMonth.from(curFirstDay.minusMonths(1));

        YearMonth cur = startYm;
        while (!cur.isAfter(endYm)) {
            String mName = cur.getMonth().name();
            int y = cur.getYear();

            Optional<PayrollRecord> pastRecordOpt = payrollRecordRepository
                    .findByEmployeeIdAndPayrollMonthIgnoreCaseAndPayrollYear(employeeId, mName, y);

            if (pastRecordOpt.isPresent() && pastRecordOpt.get().getStatus() == PayrollStatus.PUBLISHED) {
                BigDecimal pastGross = pastRecordOpt.get().getMasterFixedGross();
                BigDecimal newGross = currentStruct.getTotalFixedGross();

                if (newGross.compareTo(pastGross) > 0) {
                    BigDecimal diff = newGross.subtract(pastGross);
                    ArrearsRecord record = ArrearsRecord.builder()
                            .employeeId(employeeId)
                            .targetMonth(currentMonth.toUpperCase())
                            .targetYear(currentYear)
                            .sourceMonth(mName)
                            .sourceYear(y)
                            .arrearsAmount(diff)
                            .reason("Automatic arrears from salary hike effective " + effDate + " (Differential for " + mName + " " + y + ")")
                            .isProcessed(false)
                            .build();

                    ArrearsRecord saved = arrearsRecordRepository.save(record);
                    proposed.add(mapToDto(saved, emp));
                }
            }
            cur = cur.plusMonths(1);
        }

        return proposed;
    }

    private ArrearsDto mapToDto(ArrearsRecord r, Employee emp) {
        return ArrearsDto.builder()
                .id(r.getId())
                .employeeId(r.getEmployeeId())
                .employeeName(emp != null ? emp.getFirstName() + " " + emp.getLastName() : "Employee #" + r.getEmployeeId())
                .employeeCode(emp != null ? emp.getEmployeeCode() : "")
                .targetMonth(r.getTargetMonth())
                .targetYear(r.getTargetYear())
                .sourceMonth(r.getSourceMonth())
                .sourceYear(r.getSourceYear())
                .arrearsAmount(r.getArrearsAmount())
                .reason(r.getReason())
                .isProcessed(r.getIsProcessed())
                .processedRecordId(r.getProcessedRecordId())
                .createdAt(r.getCreatedAt())
                .build();
    }

    private int parseMonthNumber(String monthStr) {
        try {
            return Integer.parseInt(monthStr);
        } catch (Exception ignored) {}
        try {
            return java.time.Month.valueOf(monthStr.toUpperCase()).getValue();
        } catch (Exception e) {
            return LocalDate.now().getMonthValue();
        }
    }
}
