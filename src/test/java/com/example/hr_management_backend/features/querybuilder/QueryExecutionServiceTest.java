package com.example.hr_management_backend.features.querybuilder;

import com.example.hr_management_backend.features.querybuilder.compiler.QueryCompiler;
import com.example.hr_management_backend.features.querybuilder.dto.QueryRequestDto;
import com.example.hr_management_backend.features.querybuilder.service.QueryExecutionService;
import com.example.hr_management_backend.features.querybuilder.service.QueryMetadataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

class QueryExecutionServiceTest {

    private QueryExecutionService executionService;
    private NamedParameterJdbcTemplate mockJdbc;

    @BeforeEach
    void setUp() {
        QueryMetadataService metadataService = new QueryMetadataService();
        QueryCompiler compiler = new QueryCompiler(metadataService);
        mockJdbc = Mockito.mock(NamedParameterJdbcTemplate.class);
        executionService = new QueryExecutionService(compiler, mockJdbc);
    }

    @Test
    @DisplayName("Should stream valid Excel XLSX binary with PK zip header")
    void testStreamXlsx() throws Exception {
        // Mock ResultSet with 1 sample row
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(2);
            ResultSet rs = Mockito.mock(ResultSet.class);
            Mockito.when(rs.getObject("col_0")).thenReturn("EMP001");
            Mockito.when(rs.getObject("col_1")).thenReturn("Engineering");
            handler.processRow(rs);
            return null;
        }).when(mockJdbc).query(anyString(), any(SqlParameterSource.class), any(RowCallbackHandler.class));

        QueryRequestDto req = QueryRequestDto.builder()
                .rootEntity("EMPLOYEE")
                .selectedFields(List.of("employee.code", "employee.department"))
                .build();

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        executionService.streamExport(req, "XLSX", os);

        byte[] bytes = os.toByteArray();
        assertTrue(bytes.length > 100);
        // Valid ZIP / XLSX magic number: 0x50, 0x4B, 0x03, 0x04 ('P', 'K', 3, 4)
        assertEquals((byte) 0x50, bytes[0]);
        assertEquals((byte) 0x4B, bytes[1]);
        assertEquals((byte) 0x03, bytes[2]);
        assertEquals((byte) 0x04, bytes[3]);
    }

    @Test
    @DisplayName("Should stream CSV format with UTF-8 BOM and correct headers")
    void testStreamCsv() throws Exception {
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(2);
            ResultSet rs = Mockito.mock(ResultSet.class);
            Mockito.when(rs.getObject("col_0")).thenReturn("EMP001");
            Mockito.when(rs.getObject("col_1")).thenReturn("Engineering");
            handler.processRow(rs);
            return null;
        }).when(mockJdbc).query(anyString(), any(SqlParameterSource.class), any(RowCallbackHandler.class));

        QueryRequestDto req = QueryRequestDto.builder()
                .rootEntity("EMPLOYEE")
                .selectedFields(List.of("employee.code", "employee.department"))
                .build();

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        executionService.streamExport(req, "CSV", os);

        String csvOutput = os.toString(StandardCharsets.UTF_8);
        assertTrue(csvOutput.startsWith("\ufeff")); // UTF-8 BOM
        assertTrue(csvOutput.contains("\"Employee Code\",\"Department\""));
        assertTrue(csvOutput.contains("\"EMP001\",\"Engineering\""));
    }
}
