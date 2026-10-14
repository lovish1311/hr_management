package com.example.hr_management_backend.features.querybuilder.service;

import com.example.hr_management_backend.features.querybuilder.dto.EntityMetadataDto;
import com.example.hr_management_backend.features.querybuilder.dto.FieldMetadataDto;
import com.example.hr_management_backend.features.querybuilder.dto.MetadataCatalogDto;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class QueryMetadataService {

    public static class FieldDefinition {
        private final String fieldId;
        private final String label;
        private final String dataType;
        private final String sqlExpression;
        private final String tableAlias; // e, a, l, p
        private final boolean filterable;
        private final boolean projectable;
        private final List<String> allowedOperators;
        private final List<String> options;

        public FieldDefinition(String fieldId, String label, String dataType, String sqlExpression,
                               String tableAlias, boolean filterable, boolean projectable,
                               List<String> allowedOperators, List<String> options) {
            this.fieldId = fieldId;
            this.label = label;
            this.dataType = dataType;
            this.sqlExpression = sqlExpression;
            this.tableAlias = tableAlias;
            this.filterable = filterable;
            this.projectable = projectable;
            this.allowedOperators = allowedOperators;
            this.options = options;
        }

        public String getFieldId() { return fieldId; }
        public String getLabel() { return label; }
        public String getDataType() { return dataType; }
        public String getSqlExpression() { return sqlExpression; }
        public String getTableAlias() { return tableAlias; }
        public boolean isFilterable() { return filterable; }
        public boolean isProjectable() { return projectable; }
        public List<String> getAllowedOperators() { return allowedOperators; }
        public List<String> getOptions() { return options; }

        public FieldMetadataDto toDto() {
            return FieldMetadataDto.builder()
                    .fieldId(fieldId)
                    .label(label)
                    .dataType(dataType)
                    .filterable(filterable)
                    .projectable(projectable)
                    .allowedOperators(allowedOperators)
                    .options(options)
                    .build();
        }
    }

    private final Map<String, FieldDefinition> fieldRegistry = new LinkedHashMap<>();
    private final List<EntityMetadataDto> entities = new ArrayList<>();

    public QueryMetadataService() {
        initRegistry();
    }

    private void initRegistry() {
        // 1. EMPLOYEE ENTITY
        List<FieldMetadataDto> employeeFields = new ArrayList<>();
        registerField("employee.id", "Employee ID", "NUMBER", "e.id", "e",
                true, true, List.of("EQUALS", "NOT_EQUALS", "IN"), null, employeeFields);
        registerField("employee.code", "Employee Code", "STRING", "e.employee_code", "e",
                true, true, List.of("EQUALS", "NOT_EQUALS", "CONTAINS", "STARTS_WITH", "IN"), null, employeeFields);
        registerField("employee.fullName", "Full Name", "STRING", "CONCAT(e.first_name, ' ', e.last_name)", "e",
                true, true, List.of("CONTAINS", "STARTS_WITH", "EQUALS"), null, employeeFields);
        registerField("employee.email", "Email Address", "STRING", "e.email", "e",
                true, true, List.of("EQUALS", "CONTAINS", "ENDS_WITH"), null, employeeFields);
        registerField("employee.department", "Department", "ENUM", "e.department", "e",
                true, true, List.of("EQUALS", "NOT_EQUALS", "IN"),
                List.of("Engineering", "HR", "Sales", "Marketing", "Finance", "Operations", "Product"), employeeFields);
        registerField("employee.designation", "Designation", "STRING", "e.designation", "e",
                true, true, List.of("EQUALS", "CONTAINS", "IN"), null, employeeFields);
        registerField("employee.status", "Employment Status", "ENUM", "e.status", "e",
                true, true, List.of("EQUALS", "NOT_EQUALS", "IN"),
                List.of("ACTIVE", "PROBATION", "ON_LEAVE", "TERMINATED"), employeeFields);
        registerField("employee.employmentType", "Employment Type", "ENUM", "e.employment_type", "e",
                true, true, List.of("EQUALS", "NOT_EQUALS", "IN"),
                List.of("FULL_TIME", "PART_TIME", "CONTRACT", "INTERN"), employeeFields);
        registerField("employee.joiningDate", "Joining Date", "DATE", "e.joining_date", "e",
                true, true, List.of("EQUALS", "BEFORE", "AFTER", "BETWEEN"), null, employeeFields);

        entities.add(EntityMetadataDto.builder()
                .entityId("EMPLOYEE")
                .displayName("Employees & Profiles")
                .description("Workforce demographics, roles, status, and departments")
                .fields(employeeFields)
                .build());

        // 2. ATTENDANCE ENTITY
        List<FieldMetadataDto> attendanceFields = new ArrayList<>();
        registerField("attendance.date", "Attendance Date", "DATE", "a.date", "a",
                true, true, List.of("EQUALS", "BEFORE", "AFTER", "BETWEEN"), null, attendanceFields);
        registerField("attendance.status", "Daily Status", "ENUM", "a.status", "a",
                true, true, List.of("EQUALS", "NOT_EQUALS", "IN"),
                List.of("PRESENT", "ABSENT", "LATE", "HALF_DAY"), attendanceFields);
        registerField("attendance.totalWorkingMinutes", "Working Minutes", "NUMBER", "a.total_working_minutes", "a",
                true, true, List.of("EQUALS", "GREATER_THAN", "LESS_THAN", "BETWEEN"), null, attendanceFields);

        entities.add(EntityMetadataDto.builder()
                .entityId("ATTENDANCE")
                .displayName("Time & Attendance")
                .description("Daily swipes, shifts, work hours, and daily status")
                .fields(attendanceFields)
                .build());

        // 3. LEAVES ENTITY
        List<FieldMetadataDto> leaveFields = new ArrayList<>();
        registerField("leave.leaveType", "Leave Type", "ENUM", "l.leave_type", "l",
                true, true, List.of("EQUALS", "NOT_EQUALS", "IN"),
                List.of("CASUAL", "SICK", "EARNED", "UNPAID", "RESTRICTED_HOLIDAY"), leaveFields);
        registerField("leave.startDate", "Start Date", "DATE", "l.start_date", "l",
                true, true, List.of("EQUALS", "BEFORE", "AFTER", "BETWEEN"), null, leaveFields);
        registerField("leave.endDate", "End Date", "DATE", "l.end_date", "l",
                true, true, List.of("EQUALS", "BEFORE", "AFTER", "BETWEEN"), null, leaveFields);
        registerField("leave.totalDays", "Total Days", "NUMBER", "l.total_days", "l",
                true, true, List.of("EQUALS", "GREATER_THAN", "LESS_THAN", "BETWEEN"), null, leaveFields);
        registerField("leave.status", "Approval Status", "ENUM", "l.status", "l",
                true, true, List.of("EQUALS", "NOT_EQUALS", "IN"),
                List.of("PENDING", "APPROVED", "REJECTED", "CANCELLED"), leaveFields);

        entities.add(EntityMetadataDto.builder()
                .entityId("LEAVES")
                .displayName("Leaves & Time Off")
                .description("Leave requests, categories, dates, and approvals")
                .fields(leaveFields)
                .build());

        // 4. PAYROLL ENTITY
        List<FieldMetadataDto> payrollFields = new ArrayList<>();
        registerField("payroll.payrollMonth", "Payroll Month", "ENUM", "p.payroll_month", "p",
                true, true, List.of("EQUALS", "IN"),
                List.of("JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE", "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER"), payrollFields);
        registerField("payroll.payrollYear", "Payroll Year", "NUMBER", "p.payroll_year", "p",
                true, true, List.of("EQUALS", "GREATER_THAN", "LESS_THAN", "IN"), null, payrollFields);
        registerField("payroll.totalGrossEarnings", "Gross Earnings (₹)", "NUMBER", "p.total_gross_pay", "p",
                true, true, List.of("EQUALS", "GREATER_THAN", "LESS_THAN", "BETWEEN"), null, payrollFields);
        registerField("payroll.deductionEpf", "EPF Deduction (₹)", "NUMBER", "p.calculated_pf", "p",
                true, true, List.of("EQUALS", "GREATER_THAN", "LESS_THAN", "BETWEEN"), null, payrollFields);
        registerField("payroll.deductionEsi", "ESI Deduction (₹)", "NUMBER", "p.calculated_esi", "p",
                true, true, List.of("EQUALS", "GREATER_THAN", "LESS_THAN", "BETWEEN"), null, payrollFields);
        registerField("payroll.totalDeductions", "Total Deductions (₹)", "NUMBER", "p.total_deductions", "p",
                true, true, List.of("EQUALS", "GREATER_THAN", "LESS_THAN", "BETWEEN"), null, payrollFields);
        registerField("payroll.netTakeHome", "Net Take Home (₹)", "NUMBER", "p.net_pay", "p",
                true, true, List.of("EQUALS", "GREATER_THAN", "LESS_THAN", "BETWEEN"), null, payrollFields);
        registerField("payroll.status", "Payroll Status", "ENUM", "p.status", "p",
                true, true, List.of("EQUALS", "NOT_EQUALS", "IN"),
                List.of("DRAFT", "PROCESSED", "APPROVED", "PAID"), payrollFields);

        entities.add(EntityMetadataDto.builder()
                .entityId("PAYROLL")
                .displayName("Payroll & Compensation")
                .description("Monthly pay runs, gross earnings, statutory deductions, net pay")
                .fields(payrollFields)
                .build());
    }

    private void registerField(String fieldId, String label, String dataType, String sqlExpression, String tableAlias,
                               boolean filterable, boolean projectable, List<String> allowedOps, List<String> options,
                               List<FieldMetadataDto> dtoList) {
        FieldDefinition def = new FieldDefinition(fieldId, label, dataType, sqlExpression, tableAlias, filterable, projectable, allowedOps, options);
        fieldRegistry.put(fieldId, def);
        dtoList.add(def.toDto());
    }

    public MetadataCatalogDto getMetadataCatalog() {
        return MetadataCatalogDto.builder().entities(entities).build();
    }

    public FieldDefinition getField(String fieldId) {
        return fieldRegistry.get(fieldId);
    }

    public boolean isFieldRegistered(String fieldId) {
        return fieldRegistry.containsKey(fieldId);
    }

    public Map<String, FieldDefinition> getFieldRegistry() {
        return Collections.unmodifiableMap(fieldRegistry);
    }
}
