package com.example.hr_management_backend.features.querybuilder;

import com.example.hr_management_backend.features.querybuilder.compiler.QueryCompiler;
import com.example.hr_management_backend.features.querybuilder.dto.FilterGroupDto;
import com.example.hr_management_backend.features.querybuilder.dto.FilterRuleDto;
import com.example.hr_management_backend.features.querybuilder.dto.QueryRequestDto;
import com.example.hr_management_backend.features.querybuilder.service.QueryMetadataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QueryCompilerTest {

    private QueryCompiler compiler;
    private QueryMetadataService metadataService;

    @BeforeEach
    void setUp() {
        metadataService = new QueryMetadataService();
        compiler = new QueryCompiler(metadataService);
    }

    @Test
    @DisplayName("Should compile basic select query with default columns")
    void testBasicSelect() {
        QueryRequestDto req = QueryRequestDto.builder()
                .rootEntity("EMPLOYEE")
                .selectedFields(List.of("employee.code", "employee.fullName", "employee.department"))
                .page(0)
                .pageSize(20)
                .build();

        QueryCompiler.CompiledQuery compiled = compiler.compile(req);

        assertNotNull(compiled.getSelectSql());
        assertTrue(compiled.getSelectSql().contains("e.employee_code AS col_0"));
        assertTrue(compiled.getSelectSql().contains("CONCAT(e.first_name, ' ', e.last_name) AS col_1"));
        assertTrue(compiled.getSelectSql().contains("e.department AS col_2"));
        assertTrue(compiled.getSelectSql().contains("FROM employees e"));
        assertTrue(compiled.getSelectSql().contains("LIMIT :limit OFFSET :offset"));
        assertEquals(20, compiled.getParameters().getValue("limit"));
        assertEquals(0, compiled.getParameters().getValue("offset"));
        assertEquals(3, compiled.getProjectedColumns().size());
    }

    @Test
    @DisplayName("Should dynamically inject sparse joins when payroll fields are requested")
    void testSparseJoinInjection() {
        QueryRequestDto req = QueryRequestDto.builder()
                .rootEntity("EMPLOYEE")
                .selectedFields(List.of("employee.code", "payroll.netTakeHome"))
                .build();

        QueryCompiler.CompiledQuery compiled = compiler.compile(req);

        assertTrue(compiled.getSelectSql().contains("LEFT JOIN payroll_records p ON p.employee_id = e.id"));
        assertFalse(compiled.getSelectSql().contains("LEFT JOIN attendance a"));
        assertFalse(compiled.getSelectSql().contains("LEFT JOIN leave_requests l"));
    }

    @Test
    @DisplayName("Should compile nested AND / OR filter AST rules with parameterized values")
    void testNestedFilterGroupCompilation() {
        FilterGroupDto subGroup = FilterGroupDto.builder()
                .conjunction("OR")
                .rules(List.of(
                        FilterRuleDto.builder()
                                .fieldId("employee.department")
                                .operator("EQUALS")
                                .values(List.of("Engineering"))
                                .build(),
                        FilterRuleDto.builder()
                                .fieldId("payroll.netTakeHome")
                                .operator("GREATER_THAN")
                                .values(List.of("75000"))
                                .build()
                ))
                .build();

        FilterGroupDto rootGroup = FilterGroupDto.builder()
                .conjunction("AND")
                .rules(List.of(
                        FilterRuleDto.builder()
                                .fieldId("employee.status")
                                .operator("EQUALS")
                                .values(List.of("ACTIVE"))
                                .build()
                ))
                .subGroups(List.of(subGroup))
                .build();

        QueryRequestDto req = QueryRequestDto.builder()
                .rootEntity("EMPLOYEE")
                .selectedFields(List.of("employee.code", "employee.department", "payroll.netTakeHome"))
                .filterGroup(rootGroup)
                .sortBy("payroll.netTakeHome")
                .sortDirection("DESC")
                .build();

        QueryCompiler.CompiledQuery compiled = compiler.compile(req);

        String sql = compiled.getSelectSql();
        assertTrue(sql.contains("WHERE LOWER(e.status::text) = LOWER(:p_1)"));
        assertTrue(sql.contains("LOWER(e.department::text) = LOWER(:p_2)"));
        assertTrue(sql.contains("p.net_pay > :p_3"));
        assertTrue(sql.contains("ORDER BY p.net_pay DESC NULLS LAST"));

        assertEquals("ACTIVE", compiled.getParameters().getValue("p_1"));
        assertEquals("Engineering", compiled.getParameters().getValue("p_2"));
        assertEquals(75000.0, compiled.getParameters().getValue("p_3"));
    }

    @Test
    @DisplayName("Should compile BETWEEN and IN operators properly")
    void testBetweenAndInOperators() {
        FilterGroupDto group = FilterGroupDto.builder()
                .conjunction("AND")
                .rules(List.of(
                        FilterRuleDto.builder()
                                .fieldId("employee.joiningDate")
                                .operator("BETWEEN")
                                .values(List.of("2024-01-01", "2024-12-31"))
                                .build(),
                        FilterRuleDto.builder()
                                .fieldId("employee.department")
                                .operator("IN")
                                .values(List.of("Engineering", "Sales", "HR"))
                                .build()
                ))
                .build();

        QueryRequestDto req = QueryRequestDto.builder()
                .rootEntity("EMPLOYEE")
                .filterGroup(group)
                .build();

        QueryCompiler.CompiledQuery compiled = compiler.compile(req);

        String sql = compiled.getSelectSql();
        assertTrue(sql.contains("e.joining_date BETWEEN :p_1 AND :p_2"));
        assertTrue(sql.contains("e.department IN (:p_3)"));

        Object inParam = compiled.getParameters().getValue("p_3");
        assertTrue(inParam instanceof List);
        assertEquals(3, ((List<?>) inParam).size());
    }

    @Test
    @DisplayName("Should compile attendance root entity with employee join")
    void testAttendanceRootEntity() {
        QueryRequestDto req = QueryRequestDto.builder()
                .rootEntity("ATTENDANCE")
                .selectedFields(List.of("attendance.date", "employee.code", "attendance.status"))
                .page(1)
                .pageSize(50)
                .build();

        QueryCompiler.CompiledQuery compiled = compiler.compile(req);

        assertTrue(compiled.getSelectSql().contains("FROM attendance a JOIN employees e ON a.employee_id = e.id"));
        assertEquals(50, compiled.getParameters().getValue("limit"));
        assertEquals(50, compiled.getParameters().getValue("offset"));
    }
}
