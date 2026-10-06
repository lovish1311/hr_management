package com.example.hr_management_backend.features.payroll.service;

import com.example.hr_management_backend.features.attendance.model.Attendance;
import com.example.hr_management_backend.features.attendance.repository.AttendanceRepository;
import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import com.example.hr_management_backend.features.leaves.model.LeaveRequest;
import com.example.hr_management_backend.features.leaves.repository.LeaveRequestRepository;
import com.example.hr_management_backend.features.payroll.dto.*;
import com.example.hr_management_backend.features.payroll.model.*;
import com.example.hr_management_backend.features.payroll.repository.*;
import com.example.hr_management_backend.features.holidays.model.Holiday;
import com.example.hr_management_backend.features.holidays.repository.HolidayRepository;
import com.example.hr_management_backend.features.payroll.arrears.ArrearsService;
import com.example.hr_management_backend.features.payroll.tax.TaxCalculatorFactory;
import com.example.hr_management_backend.features.payroll.tax.TaxDeclarationDto;
import com.example.hr_management_backend.features.payroll.tax.TaxRegime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayrollProcessingService {

    private final SalaryStructureRepository salaryStructureRepository;
    private final MonthlyPayrollInputRepository monthlyPayrollInputRepository;
    private final PayrollRecordRepository payrollRecordRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final HolidayRepository holidayRepository;
    private final TaxCalculatorFactory taxCalculatorFactory;
    private final ArrearsService arrearsService;

    // =========================================================================
    // 1. SALARY STRUCTURE (MASTER CONFIG)
    // =========================================================================

    @Transactional
    public SalaryStructureDto saveSalaryStructure(SalaryStructureDto dto) {
        if (dto.getBasicPay() == null || dto.getBasicPay().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Basic Pay must be greater than zero.");
        }

        Employee employee = employeeRepository.findById(dto.getEmployeeId())
                .orElseThrow(() -> new IllegalArgumentException("Employee not found with ID: " + dto.getEmployeeId()));

        // Deactivate previous active structures for this employee
        if (Boolean.TRUE.equals(dto.getIsActive())) {
            List<SalaryStructure> existing = salaryStructureRepository
                    .findByEmployeeIdOrderByEffectiveDateDesc(dto.getEmployeeId());
            for (SalaryStructure s : existing) {
                if (Boolean.TRUE.equals(s.getIsActive())) {
                    s.setIsActive(false);
                    salaryStructureRepository.save(s);
                }
            }
        }

        SalaryStructure entity;
        if (dto.getId() != null) {
            entity = salaryStructureRepository.findById(dto.getId())
                    .orElseGet(SalaryStructure::new);
        } else {
            entity = new SalaryStructure();
        }

        entity.setEmployeeId(employee.getId());
        entity.setBasicPay(dto.getBasicPay());
        entity.setHra(dto.getHra() != null ? dto.getHra() : BigDecimal.ZERO);
        entity.setConveyanceAllowance(dto.getConveyanceAllowance() != null ? dto.getConveyanceAllowance() : BigDecimal.ZERO);
        entity.setMedicalAllowance(dto.getMedicalAllowance() != null ? dto.getMedicalAllowance() : BigDecimal.ZERO);
        entity.setSpecialAllowance(dto.getSpecialAllowance() != null ? dto.getSpecialAllowance() : BigDecimal.ZERO);

        entity.setPfContribution(dto.getPfContribution() != null ? dto.getPfContribution() : BigDecimal.ZERO);
        entity.setEsiContribution(dto.getEsiContribution() != null ? dto.getEsiContribution() : BigDecimal.ZERO);
        entity.setProfessionalTax(dto.getProfessionalTax() != null ? dto.getProfessionalTax() : BigDecimal.valueOf(200));
        entity.setTaxRegime(dto.getTaxRegime() != null ? dto.getTaxRegime().toUpperCase() : "NEW");
        entity.setDeclared80C(dto.getDeclared80C());
        entity.setDeclared80D(dto.getDeclared80D());
        entity.setMonthlyTdsOverride(dto.getMonthlyTdsOverride());

        entity.setEffectiveDate(dto.getEffectiveDate() != null ? dto.getEffectiveDate() : LocalDate.now());
        entity.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);

        SalaryStructure saved = salaryStructureRepository.save(entity);
        try {
            arrearsService.autoDetectAndQueueStructuralArrears(employee.getId(), LocalDate.now().getMonth().name(), LocalDate.now().getYear());
        } catch (Exception e) {
            log.warn("Arrears auto-detection notice for employee {}: {}", employee.getId(), e.getMessage());
        }
        return mapToSalaryStructureDto(saved, employee);
    }

    @Transactional(readOnly = true)
    public SalaryStructureDto getSalaryStructure(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found with ID: " + employeeId));

        return salaryStructureRepository.findByEmployeeIdAndIsActiveTrue(employeeId)
                .map(s -> mapToSalaryStructureDto(s, employee))
                .orElseGet(() -> createDefaultStructureDto(employee));
    }

    @Transactional(readOnly = true)
    public List<SalaryStructureDto> getAllActiveSalaryStructures() {
        List<Employee> employees = employeeRepository.findAll();
        Map<Long, Employee> empMap = employees.stream().collect(Collectors.toMap(Employee::getId, e -> e));

        List<SalaryStructure> activeStructures = salaryStructureRepository.findByIsActiveTrue();
        Set<Long> configuredEmpIds = activeStructures.stream().map(SalaryStructure::getEmployeeId).collect(Collectors.toSet());

        List<SalaryStructureDto> result = new ArrayList<>();
        for (SalaryStructure s : activeStructures) {
            Employee emp = empMap.get(s.getEmployeeId());
            if (emp != null) {
                result.add(mapToSalaryStructureDto(s, emp));
            }
        }

        // For any employee without a structure, offer a default baseline
        for (Employee emp : employees) {
            if (!configuredEmpIds.contains(emp.getId())) {
                result.add(createDefaultStructureDto(emp));
            }
        }

        return result;
    }

    // =========================================================================
    // 2. MONTHLY PAYROLL INPUTS (PRE-PAYROLL VARIABLES)
    // =========================================================================

    @Transactional
    public MonthlyPayrollInputDto saveMonthlyPayrollInput(MonthlyPayrollInputDto dto) {
        String month = normalizeMonth(dto.getPayrollMonth());
        Integer year = dto.getPayrollYear() != null ? dto.getPayrollYear() : LocalDate.now().getYear();
        validatePayPeriodNotFuture(month, year);
        validatePayPeriodNotPublished(month, year);

        MonthlyPayrollInput input = monthlyPayrollInputRepository
                .findByEmployeeIdAndPayrollMonthIgnoreCaseAndPayrollYear(dto.getEmployeeId(), month, year)
                .orElseGet(() -> MonthlyPayrollInput.builder()
                        .employeeId(dto.getEmployeeId())
                        .payrollMonth(month)
                        .payrollYear(year)
                        .build());

        if (Boolean.TRUE.equals(input.getIsLocked())) {
            throw new IllegalStateException("Cannot modify payroll inputs for " + month + " " + year + " because inputs are locked.");
        }

        input.setLopDays(dto.getLopDays() != null ? Math.max(0.0, dto.getLopDays()) : 0.0);
        input.setOvertimeHours(dto.getOvertimeHours() != null ? Math.max(0.0, dto.getOvertimeHours()) : 0.0);
        input.setAdHocBonus(dto.getAdHocBonus() != null ? dto.getAdHocBonus() : BigDecimal.ZERO);
        input.setAdHocDeduction(dto.getAdHocDeduction() != null ? dto.getAdHocDeduction() : BigDecimal.ZERO);
        input.setArrearsAmount(dto.getArrearsAmount() != null ? dto.getArrearsAmount() : BigDecimal.ZERO);
        input.setTaxRegime(dto.getTaxRegime() != null ? dto.getTaxRegime().toUpperCase() : "NEW");
        input.setDeclared80C(dto.getDeclared80C());
        input.setDeclared80D(dto.getDeclared80D());
        input.setIsExempt(Boolean.TRUE.equals(dto.getIsExempt()));
        input.setNotes(dto.getNotes());

        MonthlyPayrollInput saved = monthlyPayrollInputRepository.save(input);
        Employee emp = employeeRepository.findById(dto.getEmployeeId()).orElse(null);
        return mapToMonthlyPayrollInputDto(saved, emp);
    }

    @Transactional(readOnly = true)
    public List<MonthlyPayrollInputDto> getMonthlyPayrollInputs(String monthStr, Integer year) {
        String month = normalizeMonth(monthStr);
        List<MonthlyPayrollInput> inputs = monthlyPayrollInputRepository.findByPayrollMonthIgnoreCaseAndPayrollYear(month, year);
        Map<Long, MonthlyPayrollInput> inputMap = inputs.stream().collect(Collectors.toMap(MonthlyPayrollInput::getEmployeeId, i -> i));

        List<Employee> employees = employeeRepository.findAll();
        List<MonthlyPayrollInputDto> result = new ArrayList<>();

        for (Employee emp : employees) {
            MonthlyPayrollInput input = inputMap.get(emp.getId());
            if (input != null) {
                result.add(mapToMonthlyPayrollInputDto(input, emp));
            } else {
                // Return unlocked zero-state for review
                BigDecimal fixedGross = salaryStructureRepository.findByEmployeeIdAndIsActiveTrue(emp.getId())
                        .map(SalaryStructure::getTotalFixedGross)
                        .orElse(BigDecimal.valueOf(40000.0));

                result.add(MonthlyPayrollInputDto.builder()
                        .employeeId(emp.getId())
                        .employeeName(emp.getFirstName() + " " + emp.getLastName())
                        .employeeCode(emp.getEmployeeCode())
                        .department(emp.getDepartment())
                        .fixedGross(fixedGross)
                        .payrollMonth(month)
                        .payrollYear(year)
                        .lopDays(0.0)
                        .overtimeHours(0.0)
                        .adHocBonus(BigDecimal.ZERO)
                        .adHocDeduction(BigDecimal.ZERO)
                        .isLocked(false)
                        .isExempt(Boolean.TRUE.equals(emp.getIsPayrollExempt()))
                        .build());
            }
        }
        return result;
    }

    @Transactional
    public List<MonthlyPayrollInputDto> syncMonthlyPayrollInputsFromAttendance(String monthStr, Integer year) {
        validatePayPeriodNotFuture(monthStr, year);
        String month = normalizeMonth(monthStr);
        validatePayPeriodNotPublished(month, year);

        int monthNum = parseMonthNumber(month);
        YearMonth ym = YearMonth.of(year, monthNum);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        List<Employee> employees = employeeRepository.findAll();
        List<MonthlyPayrollInputDto> updated = new ArrayList<>();

        // Fetch company general holidays once for the cycle
        Set<LocalDate> holidayDates = holidayRepository.findActiveGeneralHolidaysBetween(start, end).stream()
                .map(Holiday::getDate)
                .collect(Collectors.toSet());

        for (Employee emp : employees) {
            MonthlyPayrollInput input = monthlyPayrollInputRepository
                    .findByEmployeeIdAndPayrollMonthIgnoreCaseAndPayrollYear(emp.getId(), month, year)
                    .orElseGet(() -> MonthlyPayrollInput.builder()
                            .employeeId(emp.getId())
                            .payrollMonth(month)
                            .payrollYear(year)
                            .isLocked(false)
                            .isExempt(Boolean.TRUE.equals(emp.getIsPayrollExempt()))
                            .build());

            if (Boolean.TRUE.equals(input.getIsLocked())) {
                continue; // Do not overwrite if HR previously locked this month
            }

            // Exemption check 1: If employee attendance tracking is disabled, 0 LOP
            if (Boolean.FALSE.equals(emp.getIsAttendanceTracked())) {
                input.setLopDays(0.0);
                input.setNotes("Employee attendance tracking is EXEMPT");
                BigDecimal pendingArrears = arrearsService.calculatePendingArrearsTotalForEmployee(emp.getId(), month, year);
                input.setArrearsAmount(pendingArrears != null ? pendingArrears : BigDecimal.ZERO);
                MonthlyPayrollInput saved = monthlyPayrollInputRepository.save(input);
                updated.add(mapToMonthlyPayrollInputDto(saved, emp));
                continue;
            }

            // Evaluation bounds: up to today if in current month, or end of month if in past
            LocalDate today = LocalDate.now();
            LocalDate evalEnd = end.isAfter(today) ? today : end;
            LocalDate evalStart = (emp.getJoiningDate() != null && emp.getJoiningDate().isAfter(start))
                    ? emp.getJoiningDate()
                    : start;

            List<LeaveRequest> leaves = leaveRequestRepository.findApprovedLeavesForEmployeeInRange(emp.getId(), start, end);
            List<Attendance> attendances = attendanceRepository.findByEmployeeIdAndDateBetween(emp.getId(), start, end);
            Map<LocalDate, Attendance> attendanceMap = attendances.stream()
                    .collect(Collectors.toMap(Attendance::getDate, a -> a, (a1, a2) -> a1));

            double totalLopDays = 0.0;
            int missingSheetDays = 0;

            if (!evalStart.isAfter(evalEnd)) {
                for (LocalDate date = evalStart; !date.isAfter(evalEnd); date = date.plusDays(1)) {
                    // 1. Skip Sundays and published public holidays
                    if (date.getDayOfWeek() == DayOfWeek.SUNDAY || holidayDates.contains(date)) {
                        continue;
                    }

                    // 2. Check approved leave requests
                    LocalDate d = date;
                    Optional<LeaveRequest> matchedLeave = leaves.stream()
                            .filter(l -> !d.isBefore(l.getStartDate()) && !d.isAfter(l.getEndDate()))
                            .findFirst();

                    if (matchedLeave.isPresent()) {
                        String type = matchedLeave.get().getLeaveType();
                        if ("LOP".equalsIgnoreCase(type) || "UNPAID".equalsIgnoreCase(type)) {
                            totalLopDays += 1.0;
                        }
                        continue;
                    }

                    // 3. Check recorded attendance log
                    Attendance att = attendanceMap.get(date);
                    if (att != null) {
                        String status = att.getStatus() != null ? att.getStatus().toUpperCase() : "";
                        if ("LOP_LEAVE".equals(status) || "UNEXCUSED_ABSENT".equals(status) || "ABSENT".equals(status)) {
                            totalLopDays += 1.0;
                        } else if ("HALF_DAY".equals(status)) {
                            totalLopDays += 0.5;
                        }
                    } else {
                        // 4. BIOMETRIC GAP / MISSING SHEET POLICY:
                        // No biometric sheet or punch exists for this working day!
                        totalLopDays += 1.0;
                        missingSheetDays++;
                    }
                }
            }

            input.setLopDays(totalLopDays);
            if (input.getOvertimeHours() == null) input.setOvertimeHours(0.0);
            if (input.getAdHocBonus() == null) input.setAdHocBonus(BigDecimal.ZERO);
            if (input.getAdHocDeduction() == null) input.setAdHocDeduction(BigDecimal.ZERO);

            BigDecimal pendingArrears = arrearsService.calculatePendingArrearsTotalForEmployee(emp.getId(), month, year);
            input.setArrearsAmount(pendingArrears != null ? pendingArrears : BigDecimal.ZERO);

            if (missingSheetDays > 0) {
                input.setNotes("Synced: " + totalLopDays + " LOP days (including " + missingSheetDays + " missing biometric sheet days)");
            } else {
                input.setNotes("Auto-synced from attendance logs on " + today);
            }

            MonthlyPayrollInput saved = monthlyPayrollInputRepository.save(input);
            updated.add(mapToMonthlyPayrollInputDto(saved, emp));
        }

        log.info("Synced attendance with biometric gap policy for {} {} across {} employees.", month, year, updated.size());
        return updated;
    }

    @Transactional
    public void setLockStateForMonth(String monthStr, Integer year, boolean lock) {
        validatePayPeriodNotFuture(monthStr, year);
        String month = normalizeMonth(monthStr);
        validatePayPeriodNotPublished(month, year);

        List<MonthlyPayrollInput> inputs = monthlyPayrollInputRepository.findByPayrollMonthIgnoreCaseAndPayrollYear(month, year);
        for (MonthlyPayrollInput i : inputs) {
            i.setIsLocked(lock);
            monthlyPayrollInputRepository.save(i);
        }
        log.info("Payroll inputs for {} {} lock status updated to: {}", month, year, lock);
    }

    // =========================================================================
    // 3. CORE CALCULATION ENGINE
    // =========================================================================

    @Transactional
    public List<PayrollRecordDto> processBatchPayroll(String monthStr, Integer year) {
        validatePayPeriodNotFuture(monthStr, year);
        String month = normalizeMonth(monthStr);
        validatePayPeriodNotPublished(month, year);

        int monthNum = parseMonthNumber(month);
        YearMonth ym = YearMonth.of(year, monthNum);
        int daysInMonth = ym.lengthOfMonth();

        List<Employee> employees = employeeRepository.findAll();
        List<PayrollRecordDto> calculated = new ArrayList<>();

        for (Employee emp : employees) {
            MonthlyPayrollInput input = monthlyPayrollInputRepository
                    .findByEmployeeIdAndPayrollMonthIgnoreCaseAndPayrollYear(emp.getId(), month, year)
                    .orElseGet(() -> MonthlyPayrollInput.builder()
                            .employeeId(emp.getId())
                            .payrollMonth(month)
                            .payrollYear(year)
                            .lopDays(0.0)
                            .overtimeHours(0.0)
                            .adHocBonus(BigDecimal.ZERO)
                            .adHocDeduction(BigDecimal.ZERO)
                            .isLocked(false)
                            .isExempt(Boolean.TRUE.equals(emp.getIsPayrollExempt()))
                            .build());

            // Payroll Exemption handling: exempt records have zero payout and do not export to bank files
            if (Boolean.TRUE.equals(input.getIsExempt()) || Boolean.TRUE.equals(emp.getIsPayrollExempt())) {
                PayrollRecord exemptRecord = payrollRecordRepository
                        .findByEmployeeIdAndPayrollMonthIgnoreCaseAndPayrollYear(emp.getId(), month, year)
                        .orElseGet(PayrollRecord::new);

                exemptRecord.setEmployeeId(emp.getId());
                exemptRecord.setEmployeeName(emp.getFirstName() + " " + emp.getLastName());
                exemptRecord.setEmployeeCode(emp.getEmployeeCode());
                exemptRecord.setDesignation(emp.getDesignation());
                exemptRecord.setDepartment(emp.getDepartment());
                exemptRecord.setBankAccountNumber(emp.getEmployeeCode() != null ? "EXEMPT" : "");
                exemptRecord.setPayrollMonth(month);
                exemptRecord.setPayrollYear(year);
                exemptRecord.setTotalDaysInMonth(daysInMonth);
                exemptRecord.setPaidDays(0.0);
                exemptRecord.setLopDays(0.0);
                exemptRecord.setOvertimeHours(0.0);
                exemptRecord.setMasterFixedGross(BigDecimal.ZERO);
                exemptRecord.setCalculatedBasic(BigDecimal.ZERO);
                exemptRecord.setCalculatedHra(BigDecimal.ZERO);
                exemptRecord.setCalculatedConveyance(BigDecimal.ZERO);
                exemptRecord.setCalculatedMedical(BigDecimal.ZERO);
                exemptRecord.setCalculatedSpecial(BigDecimal.ZERO);
                exemptRecord.setOvertimeAmount(BigDecimal.ZERO);
                exemptRecord.setAdHocBonus(BigDecimal.ZERO);
                exemptRecord.setArrearsAmount(BigDecimal.ZERO);
                exemptRecord.setCalculatedPf(BigDecimal.ZERO);
                exemptRecord.setCalculatedEsi(BigDecimal.ZERO);
                exemptRecord.setCalculatedPt(BigDecimal.ZERO);
                exemptRecord.setLopDeductionAmount(BigDecimal.ZERO);
                exemptRecord.setAdHocDeduction(BigDecimal.ZERO);
                exemptRecord.setCalculatedTds(BigDecimal.ZERO);
                exemptRecord.setTotalGrossPay(BigDecimal.ZERO);
                exemptRecord.setTotalDeductions(BigDecimal.ZERO);
                exemptRecord.setNetPay(BigDecimal.ZERO);
                exemptRecord.setStatus(PayrollStatus.EXEMPT);
                exemptRecord.setProcessedAt(LocalDateTime.now());

                PayrollRecord saved = payrollRecordRepository.save(exemptRecord);
                calculated.add(mapToPayrollRecordDto(saved));
                continue;
            }

            SalaryStructure structure = salaryStructureRepository.findByEmployeeIdAndIsActiveTrue(emp.getId())
                    .orElseGet(() -> createDefaultStructureEntity(emp));

            PayrollRecord record = calculatePayrollRecord(emp, structure, input, daysInMonth, month, year);
            record.setStatus(PayrollStatus.DRAFT);
            record.setProcessedAt(LocalDateTime.now());

            PayrollRecord saved = payrollRecordRepository.save(record);
            calculated.add(mapToPayrollRecordDto(saved));
        }

        log.info("Batch payroll calculated for {} {} across {} employees.", month, year, calculated.size());
        return calculated;
    }


    @Transactional
    public void verifyBatchPayroll(String monthStr, Integer year) {
        String month = normalizeMonth(monthStr);
        List<PayrollRecord> records = payrollRecordRepository.findByPayrollMonthIgnoreCaseAndPayrollYear(month, year);
        if (records.isEmpty()) {
            throw new IllegalStateException("No payroll records exist for " + month + " " + year + ". Process payroll first.");
        }
        for (PayrollRecord r : records) {
            if (r.getStatus() == PayrollStatus.DRAFT) {
                r.setStatus(PayrollStatus.VERIFIED);
                payrollRecordRepository.save(r);
            }
        }
        log.info("Batch payroll for {} {} transitioned to VERIFIED.", month, year);
    }

    // =========================================================================
    // 4. POST-PAYROLL & PUBLISHING (ESS & ADMIN)
    // =========================================================================

    @Transactional
    public void publishBatchPayroll(String monthStr, Integer year) {
        validatePayPeriodNotFuture(monthStr, year);
        String month = normalizeMonth(monthStr);
        List<PayrollRecord> records = payrollRecordRepository.findByPayrollMonthIgnoreCaseAndPayrollYear(month, year);
        if (records.isEmpty()) {
            throw new IllegalStateException("No payroll records exist for " + month + " " + year + " to publish.");
        }

        LocalDateTime now = LocalDateTime.now();
        for (PayrollRecord r : records) {
            r.setStatus(PayrollStatus.PUBLISHED);
            r.setPublishedAt(now);
            payrollRecordRepository.save(r);
            arrearsService.markArrearsAsProcessed(r.getEmployeeId(), month, year, r.getId());
        }
        log.info("Batch payroll for {} {} PUBLISHED to all employees.", month, year);
    }

    @Transactional(readOnly = true)
    public List<PayrollRecordDto> getPayslipsForEmployee(Long employeeId, boolean onlyPublished) {
        List<PayrollRecord> records;
        if (onlyPublished) {
            records = payrollRecordRepository.findByEmployeeIdAndStatusOrderByPayrollYearDescProcessedAtDesc(
                    employeeId, PayrollStatus.PUBLISHED);
        } else {
            records = payrollRecordRepository.findByEmployeeIdOrderByPayrollYearDescProcessedAtDesc(employeeId);
        }

        // If no records exist yet, seed a historical published record so the screen immediately renders
        if (records.isEmpty() && onlyPublished) {
            seedInitialPublishedPayslip(employeeId);
            records = payrollRecordRepository.findByEmployeeIdAndStatusOrderByPayrollYearDescProcessedAtDesc(
                    employeeId, PayrollStatus.PUBLISHED);
        }

        return records.stream().map(this::mapToPayrollRecordDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PayrollRecordDto getPayslipById(UUID recordId) {
        PayrollRecord record = payrollRecordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("Payslip not found with ID: " + recordId));
        return mapToPayrollRecordDto(record);
    }

    @Transactional(readOnly = true)
    public List<PayrollRecordDto> getPayrollRecordsForMonth(String monthStr, Integer year) {
        String month = normalizeMonth(monthStr);
        List<PayrollRecord> records = payrollRecordRepository.findByPayrollMonthIgnoreCaseAndPayrollYear(month, year);
        return records.stream().map(this::mapToPayrollRecordDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PayrollSummaryDto getPayrollSummary(String monthStr, Integer year) {
        String month = normalizeMonth(monthStr);
        List<PayrollRecord> records = payrollRecordRepository.findByPayrollMonthIgnoreCaseAndPayrollYear(month, year);

        BigDecimal grossTotal = BigDecimal.ZERO;
        BigDecimal deductionsTotal = BigDecimal.ZERO;
        BigDecimal netTotal = BigDecimal.ZERO;

        int draft = 0;
        int verified = 0;
        int published = 0;
        List<String> anomalies = new ArrayList<>();

        for (PayrollRecord r : records) {
            grossTotal = grossTotal.add(r.getTotalGrossPay());
            deductionsTotal = deductionsTotal.add(r.getTotalDeductions());
            netTotal = netTotal.add(r.getNetPay());

            if (r.getStatus() == PayrollStatus.DRAFT) draft++;
            else if (r.getStatus() == PayrollStatus.VERIFIED) verified++;
            else if (r.getStatus() == PayrollStatus.PUBLISHED) published++;

            // Flag anomalies
            if (r.getLopDays() != null && r.getLopDays() >= 3.0) {
                anomalies.add(r.getEmployeeName() + " (" + r.getEmployeeCode() + ") has " + r.getLopDays() + " LOP days deducted.");
            }
            if (r.getAdHocBonus() != null && r.getAdHocBonus().compareTo(BigDecimal.valueOf(10000)) > 0) {
                anomalies.add(r.getEmployeeName() + " has large ad-hoc bonus: ₹" + r.getAdHocBonus());
            }
            if (r.getTotalDeductions() != null && r.getTotalGrossPay() != null && r.getTotalDeductions().compareTo(r.getTotalGrossPay()) > 0) {
                anomalies.add(r.getEmployeeName() + " (" + r.getEmployeeCode() + ") deductions (₹" + r.getTotalDeductions() + ") exceed gross pay (₹" + r.getTotalGrossPay() + "). Net payout clamped at ₹0.");
            }
        }

        // Compare against prior month
        int monthNum = parseMonthNumber(month);
        YearMonth priorYm = YearMonth.of(year, monthNum).minusMonths(1);
        String priorMonthStr = priorYm.getMonth().name();
        int priorYear = priorYm.getYear();

        List<PayrollRecord> priorRecords = payrollRecordRepository.findByPayrollMonthIgnoreCaseAndPayrollYear(priorMonthStr, priorYear);
        BigDecimal priorNet = priorRecords.stream()
                .map(PayrollRecord::getNetPay)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Double variancePct = 0.0;
        if (priorNet.compareTo(BigDecimal.ZERO) > 0 && netTotal.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal diff = netTotal.subtract(priorNet);
            variancePct = diff.divide(priorNet, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }

        return PayrollSummaryDto.builder()
                .payrollMonth(month)
                .payrollYear(year)
                .totalHeadcount(records.size())
                .processedCount(records.size())
                .draftCount(draft)
                .verifiedCount(verified)
                .publishedCount(published)
                .totalGrossOutflow(grossTotal)
                .totalDeductionsOutflow(deductionsTotal)
                .totalNetPayout(netTotal)
                .priorMonthNetPayout(priorNet)
                .variancePercentage(variancePct)
                .anomalies(anomalies)
                .build();
    }

    // =========================================================================
    // CALCULATION MATH CORE (BIGDECIMAL SCALE 2)
    // =========================================================================

    private PayrollRecord calculatePayrollRecord(Employee emp, SalaryStructure struct, MonthlyPayrollInput input,
                                                 int daysInMonth, String month, Integer year) {
        int monthNum = parseMonthNumber(month);
        int preJoiningDays = 0;
        if (emp.getJoiningDate() != null) {
            if (emp.getJoiningDate().getYear() == year && emp.getJoiningDate().getMonthValue() == monthNum) {
                preJoiningDays = Math.max(0, emp.getJoiningDate().getDayOfMonth() - 1);
            } else if (emp.getJoiningDate().isAfter(YearMonth.of(year, monthNum).atEndOfMonth())) {
                preJoiningDays = daysInMonth;
            }
        }

        double lopDays = input.getLopDays() != null ? Math.min(daysInMonth, Math.max(0.0, input.getLopDays())) : 0.0;
        double nonWorkingDays = Math.min(daysInMonth, preJoiningDays + lopDays);
        double paidDays = Math.max(0.0, daysInMonth - nonWorkingDays);
        double otHours = input.getOvertimeHours() != null ? Math.max(0.0, input.getOvertimeHours()) : 0.0;
        BigDecimal adHocBonus = input.getAdHocBonus() != null ? input.getAdHocBonus() : BigDecimal.ZERO;
        BigDecimal adHocDeduction = input.getAdHocDeduction() != null ? input.getAdHocDeduction() : BigDecimal.ZERO;

        BigDecimal totalFixedGross = struct.getTotalFixedGross();

        // 1. Per-Day Gross = Total Fixed Gross / Days in Month (scale 4)
        BigDecimal perDayGross = totalFixedGross.divide(BigDecimal.valueOf(daysInMonth), 4, RoundingMode.HALF_UP);

        // 2. LOP Amount = Per-Day Gross * lopDays (scale 2)
        BigDecimal lopAmount = perDayGross.multiply(BigDecimal.valueOf(lopDays)).setScale(2, RoundingMode.HALF_UP);

        // 3. Earned Fixed Gross = Per-Day Gross * paidDays
        BigDecimal earnedFixedGross = perDayGross.multiply(BigDecimal.valueOf(paidDays)).min(totalFixedGross).setScale(2, RoundingMode.HALF_UP);

        // Pro-rata ratio for component breakdown with full precision
        BigDecimal proRataFactor = daysInMonth > 0 && paidDays > 0
                ? BigDecimal.valueOf(paidDays).divide(BigDecimal.valueOf(daysInMonth), 8, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal calcHra = struct.getHra().multiply(proRataFactor).setScale(2, RoundingMode.HALF_UP);
        BigDecimal calcConveyance = struct.getConveyanceAllowance().multiply(proRataFactor).setScale(2, RoundingMode.HALF_UP);
        BigDecimal calcMedical = struct.getMedicalAllowance().multiply(proRataFactor).setScale(2, RoundingMode.HALF_UP);
        BigDecimal calcSpecial = struct.getSpecialAllowance().multiply(proRataFactor).setScale(2, RoundingMode.HALF_UP);
        // Base is balanced so total components exactly equal earnedFixedGross
        BigDecimal otherComponentsSum = calcHra.add(calcConveyance).add(calcMedical).add(calcSpecial);
        BigDecimal calcBasic = earnedFixedGross.subtract(otherComponentsSum).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        // 4. Overtime calculation: (Per-Day Gross / 8 hrs) * 1.5 OT multiplier
        BigDecimal hourlyRate = perDayGross.divide(BigDecimal.valueOf(8), 4, RoundingMode.HALF_UP);
        BigDecimal otRate = hourlyRate.multiply(BigDecimal.valueOf(1.5));
        BigDecimal otAmount = otRate.multiply(BigDecimal.valueOf(otHours)).setScale(2, RoundingMode.HALF_UP);

        // 5. Arrears & Total Gross Pay = Earned Fixed Gross + Overtime + Ad-Hoc Bonus + Arrears
        BigDecimal arrears = input.getArrearsAmount() != null ? input.getArrearsAmount() : BigDecimal.ZERO;
        BigDecimal totalGross = earnedFixedGross.add(otAmount).add(adHocBonus).add(arrears).setScale(2, RoundingMode.HALF_UP);

        // 6. Statutory Deductions
        BigDecimal calcPf = struct.getPfContribution().setScale(2, RoundingMode.HALF_UP);
        BigDecimal calcEsi = struct.getEsiContribution().setScale(2, RoundingMode.HALF_UP);
        BigDecimal calcPt = struct.getProfessionalTax().setScale(2, RoundingMode.HALF_UP);

        // 7. Income Tax (TDS) Calculation via Strategy Pattern
        TaxRegime regime = TaxRegime.NEW;
        if (input.getTaxRegime() != null && !input.getTaxRegime().isBlank()) {
            try { regime = TaxRegime.valueOf(input.getTaxRegime().toUpperCase()); } catch (Exception ignored) {}
        } else if (struct.getTaxRegime() != null && !struct.getTaxRegime().isBlank()) {
            try { regime = TaxRegime.valueOf(struct.getTaxRegime().toUpperCase()); } catch (Exception ignored) {}
        }

        TaxDeclarationDto declaration = TaxDeclarationDto.builder()
                .regime(regime)
                .section80C(input.getDeclared80C() != null ? input.getDeclared80C() : struct.getDeclared80C())
                .section80D(input.getDeclared80D() != null ? input.getDeclared80D() : struct.getDeclared80D())
                .monthlyTdsOverride(struct.getMonthlyTdsOverride())
                .build();

        BigDecimal calcTds = taxCalculatorFactory.getStrategy(regime).calculateMonthlyTds(totalGross, declaration);

        // 8. Total Deductions = Statutory + Ad-Hoc Deduction + TDS
        BigDecimal totalDeductions = calcPf.add(calcEsi).add(calcPt).add(adHocDeduction).add(calcTds).setScale(2, RoundingMode.HALF_UP);

        // 9. Net Take-Home Pay
        BigDecimal netPay = totalGross.subtract(totalDeductions).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        // Find existing record to preserve ID or create new
        PayrollRecord record = payrollRecordRepository
                .findByEmployeeIdAndPayrollMonthIgnoreCaseAndPayrollYear(emp.getId(), month, year)
                .orElseGet(PayrollRecord::new);

        record.setEmployeeId(emp.getId());
        record.setEmployeeName(emp.getFirstName() + " " + emp.getLastName());
        record.setEmployeeCode(emp.getEmployeeCode());
        record.setDesignation(emp.getDesignation());
        record.setDepartment(emp.getDepartment());
        record.setBankAccountNumber("•••• •••• " + (1000 + (emp.getId().intValue() * 123) % 9000));

        record.setPayrollMonth(month);
        record.setPayrollYear(year);

        record.setTotalDaysInMonth(daysInMonth);
        record.setPaidDays(paidDays);
        record.setLopDays(lopDays);
        record.setOvertimeHours(otHours);

        record.setMasterFixedGross(totalFixedGross);
        record.setCalculatedBasic(calcBasic);
        record.setCalculatedHra(calcHra);
        record.setCalculatedConveyance(calcConveyance);
        record.setCalculatedMedical(calcMedical);
        record.setCalculatedSpecial(calcSpecial);
        record.setOvertimeAmount(otAmount);
        record.setAdHocBonus(adHocBonus);

        record.setCalculatedPf(calcPf);
        record.setCalculatedEsi(calcEsi);
        record.setCalculatedPt(calcPt);
        record.setLopDeductionAmount(lopAmount);
        record.setAdHocDeduction(adHocDeduction);

        record.setTotalGrossPay(totalGross);
        record.setTotalDeductions(totalDeductions);
        record.setNetPay(netPay);
        record.setArrearsAmount(arrears);
        record.setCalculatedTds(calcTds);
        record.setTaxRegime(regime.name());

        return record;
    }

    // =========================================================================
    // MAPPERS & UTILITIES
    // =========================================================================

    private SalaryStructureDto mapToSalaryStructureDto(SalaryStructure s, Employee emp) {
        return SalaryStructureDto.builder()
                .id(s.getId())
                .employeeId(s.getEmployeeId())
                .employeeName(emp != null ? emp.getFirstName() + " " + emp.getLastName() : "Employee #" + s.getEmployeeId())
                .employeeCode(emp != null ? emp.getEmployeeCode() : "")
                .department(emp != null ? emp.getDepartment() : "")
                .designation(emp != null ? emp.getDesignation() : "")
                .basicPay(s.getBasicPay())
                .hra(s.getHra())
                .conveyanceAllowance(s.getConveyanceAllowance())
                .medicalAllowance(s.getMedicalAllowance())
                .specialAllowance(s.getSpecialAllowance())
                .pfContribution(s.getPfContribution())
                .esiContribution(s.getEsiContribution())
                .professionalTax(s.getProfessionalTax())
                .taxRegime(s.getTaxRegime())
                .declared80C(s.getDeclared80C())
                .declared80D(s.getDeclared80D())
                .monthlyTdsOverride(s.getMonthlyTdsOverride())
                .effectiveDate(s.getEffectiveDate())
                .isActive(s.getIsActive())
                .totalFixedGross(s.getTotalFixedGross())
                .totalStatutoryDeductions(s.getTotalStatutoryDeductions())
                .netCtc(s.getNetCtc())
                .build();
    }

    private MonthlyPayrollInputDto mapToMonthlyPayrollInputDto(MonthlyPayrollInput i, Employee emp) {
        BigDecimal fixedGross = salaryStructureRepository.findByEmployeeIdAndIsActiveTrue(i.getEmployeeId())
                .map(SalaryStructure::getTotalFixedGross)
                .orElse(BigDecimal.valueOf(40000.0));

        return MonthlyPayrollInputDto.builder()
                .id(i.getId())
                .employeeId(i.getEmployeeId())
                .employeeName(emp != null ? emp.getFirstName() + " " + emp.getLastName() : "Employee #" + i.getEmployeeId())
                .employeeCode(emp != null ? emp.getEmployeeCode() : "")
                .department(emp != null ? emp.getDepartment() : "")
                .fixedGross(fixedGross)
                .payrollMonth(i.getPayrollMonth())
                .payrollYear(i.getPayrollYear())
                .lopDays(i.getLopDays())
                .overtimeHours(i.getOvertimeHours())
                .adHocBonus(i.getAdHocBonus())
                .adHocDeduction(i.getAdHocDeduction())
                .arrearsAmount(i.getArrearsAmount())
                .taxRegime(i.getTaxRegime())
                .declared80C(i.getDeclared80C())
                .declared80D(i.getDeclared80D())
                .isLocked(i.getIsLocked())
                .isExempt(Boolean.TRUE.equals(i.getIsExempt()))
                .notes(i.getNotes())
                .build();
    }

    private PayrollRecordDto mapToPayrollRecordDto(PayrollRecord r) {
        return PayrollRecordDto.builder()
                .id(r.getId())
                .employeeId(r.getEmployeeId())
                .employeeName(r.getEmployeeName())
                .employeeCode(r.getEmployeeCode())
                .designation(r.getDesignation())
                .department(r.getDepartment())
                .bankAccountNumber(r.getBankAccountNumber())
                .payrollMonth(r.getPayrollMonth())
                .payrollYear(r.getPayrollYear())
                .totalDaysInMonth(r.getTotalDaysInMonth())
                .paidDays(r.getPaidDays())
                .lopDays(r.getLopDays())
                .overtimeHours(r.getOvertimeHours())
                .masterFixedGross(r.getMasterFixedGross())
                .calculatedBasic(r.getCalculatedBasic())
                .calculatedHra(r.getCalculatedHra())
                .calculatedConveyance(r.getCalculatedConveyance())
                .calculatedMedical(r.getCalculatedMedical())
                .calculatedSpecial(r.getCalculatedSpecial())
                .overtimeAmount(r.getOvertimeAmount())
                .adHocBonus(r.getAdHocBonus())
                .arrearsAmount(r.getArrearsAmount())
                .calculatedPf(r.getCalculatedPf())
                .calculatedEsi(r.getCalculatedEsi())
                .calculatedPt(r.getCalculatedPt())
                .lopDeductionAmount(r.getLopDeductionAmount())
                .adHocDeduction(r.getAdHocDeduction())
                .calculatedTds(r.getCalculatedTds())
                .taxRegime(r.getTaxRegime())
                .totalGrossPay(r.getTotalGrossPay())
                .totalDeductions(r.getTotalDeductions())
                .netPay(r.getNetPay())
                .status(r.getStatus())
                .processedAt(r.getProcessedAt())
                .publishedAt(r.getPublishedAt())
                .build();
    }

    private SalaryStructureDto createDefaultStructureDto(Employee emp) {
        BigDecimal basic = BigDecimal.valueOf(30000.0);
        BigDecimal hra = BigDecimal.valueOf(12000.0);
        BigDecimal conveyance = BigDecimal.valueOf(3000.0);
        BigDecimal medical = BigDecimal.valueOf(2500.0);
        BigDecimal special = BigDecimal.valueOf(2500.0);
        BigDecimal pf = BigDecimal.valueOf(1800.0);
        BigDecimal esi = BigDecimal.ZERO;
        BigDecimal pt = BigDecimal.valueOf(200.0);

        SalaryStructure s = SalaryStructure.builder()
                .employeeId(emp.getId())
                .basicPay(basic)
                .hra(hra)
                .conveyanceAllowance(conveyance)
                .medicalAllowance(medical)
                .specialAllowance(special)
                .pfContribution(pf)
                .esiContribution(esi)
                .professionalTax(pt)
                .effectiveDate(LocalDate.now().withDayOfMonth(1))
                .isActive(true)
                .build();

        return mapToSalaryStructureDto(s, emp);
    }

    private SalaryStructure createDefaultStructureEntity(Employee emp) {
        return salaryStructureRepository.save(SalaryStructure.builder()
                .employeeId(emp.getId())
                .basicPay(BigDecimal.valueOf(30000.0))
                .hra(BigDecimal.valueOf(12000.0))
                .conveyanceAllowance(BigDecimal.valueOf(3000.0))
                .medicalAllowance(BigDecimal.valueOf(2500.0))
                .specialAllowance(BigDecimal.valueOf(2500.0))
                .pfContribution(BigDecimal.valueOf(1800.0))
                .esiContribution(BigDecimal.ZERO)
                .professionalTax(BigDecimal.valueOf(200.0))
                .effectiveDate(LocalDate.now().withDayOfMonth(1))
                .isActive(true)
                .build());
    }

    @Transactional
    public void seedInitialPublishedPayslip(Long employeeId) {
        Employee emp = employeeRepository.findById(employeeId).orElse(null);
        if (emp == null) return;

        SalaryStructure struct = salaryStructureRepository.findByEmployeeIdAndIsActiveTrue(employeeId)
                .orElseGet(() -> createDefaultStructureEntity(emp));

        MonthlyPayrollInput input = monthlyPayrollInputRepository
                .findByEmployeeIdAndPayrollMonthIgnoreCaseAndPayrollYear(employeeId, "SEPTEMBER", 2026)
                .orElseGet(() -> monthlyPayrollInputRepository.save(MonthlyPayrollInput.builder()
                        .employeeId(employeeId)
                        .payrollMonth("SEPTEMBER")
                        .payrollYear(2026)
                        .lopDays(0.0)
                        .overtimeHours(0.0)
                        .adHocBonus(BigDecimal.ZERO)
                        .adHocDeduction(BigDecimal.ZERO)
                        .isLocked(true)
                        .build()));

        PayrollRecord record = calculatePayrollRecord(emp, struct, input, 30, "SEPTEMBER", 2026);
        record.setStatus(PayrollStatus.PUBLISHED);
        record.setProcessedAt(LocalDateTime.of(2026, 9, 28, 17, 30));
        record.setPublishedAt(LocalDateTime.of(2026, 9, 29, 9, 0));
        payrollRecordRepository.save(record);
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

    public void validatePayPeriodNotFuture(String monthStr, Integer year) {
        String month = normalizeMonth(monthStr);
        int monthNum = parseMonthNumber(month);
        int targetYear = (year != null && year > 0) ? year : LocalDate.now().getYear();
        YearMonth requestedYm = YearMonth.of(targetYear, monthNum);
        YearMonth currentYm = YearMonth.now();
        if (requestedYm.isAfter(currentYm)) {
            throw new IllegalArgumentException("Cannot process or modify payroll for future period: " + month + " " + targetYear + ". Current active period is " + currentYm.getMonth().name() + " " + currentYm.getYear() + ".");
        }
    }

    public void validatePayPeriodNotPublished(String monthStr, Integer year) {
        String month = normalizeMonth(monthStr);
        int targetYear = (year != null && year > 0) ? year : LocalDate.now().getYear();
        boolean isPublished = payrollRecordRepository
                .findByPayrollMonthIgnoreCaseAndPayrollYear(month, targetYear)
                .stream()
                .anyMatch(r -> r.getStatus() == PayrollStatus.PUBLISHED);
        if (isPublished) {
            throw new IllegalStateException("Pay cycle for " + month + " " + targetYear + " is PUBLISHED and permanently frozen for compliance. Modifications or recalculations are prohibited.");
        }
    }
}
