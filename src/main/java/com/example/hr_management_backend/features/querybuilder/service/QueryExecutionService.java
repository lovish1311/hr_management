package com.example.hr_management_backend.features.querybuilder.service;

import com.example.hr_management_backend.features.querybuilder.compiler.QueryCompiler;
import com.example.hr_management_backend.features.querybuilder.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueryExecutionService {

    private final QueryCompiler queryCompiler;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public QueryPreviewResponseDto previewQuery(QueryRequestDto request) {
        long startTime = System.currentTimeMillis();
        QueryCompiler.CompiledQuery compiled = queryCompiler.compile(request);

        // 1. Get total record count matching criteria
        Long totalMatching = jdbcTemplate.queryForObject(
                compiled.getCountSql(),
                compiled.getParameters(),
                Long.class
        );
        if (totalMatching == null) totalMatching = 0L;

        // 2. Fetch paginated preview rows
        List<Map<String, Object>> rows = jdbcTemplate.query(
                compiled.getSelectSql(),
                compiled.getParameters(),
                (rs, rowNum) -> mapResultSetRow(rs, compiled)
        );

        // 3. Compute KPI summary metrics
        KpiSummaryDto kpiSummary = computeKpiSummary(rows, totalMatching, compiled.getProjectedColumns());

        long executionTime = System.currentTimeMillis() - startTime;

        return QueryPreviewResponseDto.builder()
                .columns(compiled.getProjectedColumns())
                .rows(rows)
                .totalMatchingRecords(totalMatching)
                .kpiSummary(kpiSummary)
                .currentPage(request.getPage() != null ? request.getPage() : 0)
                .pageSize(request.getPageSize() != null ? request.getPageSize() : 50)
                .executionTimeMs(executionTime)
                .build();
    }

    public QueryCountResponseDto countQuery(QueryRequestDto request) {
        long startTime = System.currentTimeMillis();
        // Zero-projection compile just for count
        QueryRequestDto countReq = QueryRequestDto.builder()
                .rootEntity(request.getRootEntity())
                .selectedFields(List.of("employee.id"))
                .filterGroup(request.getFilterGroup())
                .page(0)
                .pageSize(1)
                .build();

        QueryCompiler.CompiledQuery compiled = queryCompiler.compile(countReq);
        Long count = jdbcTemplate.queryForObject(compiled.getCountSql(), compiled.getParameters(), Long.class);
        if (count == null) count = 0L;

        long executionTime = System.currentTimeMillis() - startTime;
        return QueryCountResponseDto.builder()
                .estimatedCount(count)
                .executionTimeMs(executionTime)
                .build();
    }

    public void streamExport(QueryRequestDto request, String format, OutputStream os) throws Exception {
        // Prepare request for full export (limit 50,000 safety bound)
        QueryRequestDto exportReq = QueryRequestDto.builder()
                .rootEntity(request.getRootEntity())
                .selectedFields(request.getSelectedFields())
                .columnAliases(request.getColumnAliases())
                .filterGroup(request.getFilterGroup())
                .sortBy(request.getSortBy())
                .sortDirection(request.getSortDirection())
                .page(0)
                .pageSize(50000)
                .build();

        QueryCompiler.CompiledQuery compiled = queryCompiler.compile(exportReq);

        if ("CSV".equalsIgnoreCase(format)) {
            streamCsv(compiled, os);
        } else {
            streamXlsx(compiled, os);
        }
    }

    private void streamXlsx(QueryCompiler.CompiledQuery compiled, OutputStream os) throws Exception {
        // Low-memory streaming workbook with 100-row sliding window (O(1) memory)
        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        workbook.setCompressTempFiles(true);

        try {
            SXSSFSheet sheet = workbook.createSheet("TeamJoy Report");
            sheet.trackAllColumnsForAutoSizing();

            // Setup Corporate Header Style: Navy/Slate background (#1E293B), Bold White Font
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setFontHeightInPoints((short) 11);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.LEFT);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            // Setup Alternate Data Row Styles
            DataFormat dataFormat = workbook.createDataFormat();

            CellStyle currencyStyle = workbook.createCellStyle();
            currencyStyle.setDataFormat(dataFormat.getFormat("₹ #,##0.00"));
            currencyStyle.setBorderBottom(BorderStyle.HAIR);

            CellStyle numberStyle = workbook.createCellStyle();
            numberStyle.setDataFormat(dataFormat.getFormat("#,##0.00"));
            numberStyle.setBorderBottom(BorderStyle.HAIR);

            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(dataFormat.getFormat("yyyy-mm-dd"));
            dateStyle.setBorderBottom(BorderStyle.HAIR);

            CellStyle textStyle = workbook.createCellStyle();
            textStyle.setBorderBottom(BorderStyle.HAIR);

            // Create Header Row
            List<ColumnMetaDto> columns = compiled.getProjectedColumns();
            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(26);

            for (int i = 0; i < columns.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns.get(i).getLabel());
                cell.setCellStyle(headerStyle);
            }

            // Stream Rows via JdbcTemplate RowCallbackHandler
            final int[] rowCounter = {1};
            jdbcTemplate.query(compiled.getSelectSql(), compiled.getParameters(), rs -> {
                Row row = sheet.createRow(rowCounter[0]++);
                for (int i = 0; i < columns.size(); i++) {
                    ColumnMetaDto colMeta = columns.get(i);
                    String sqlAlias = "col_" + i;
                    Cell cell = row.createCell(i);

                    try {
                        Object val = rs.getObject(sqlAlias);
                        if (val == null) {
                            cell.setCellValue("");
                            cell.setCellStyle(textStyle);
                        } else if ("NUMBER".equals(colMeta.getDataType())) {
                            double numVal = ((Number) val).doubleValue();
                            cell.setCellValue(numVal);
                            if (colMeta.getFieldId().contains("Earnings") || colMeta.getFieldId().contains("netTakeHome")
                                    || colMeta.getFieldId().contains("Deduction") || colMeta.getFieldId().contains("totalDeductions")) {
                                cell.setCellStyle(currencyStyle);
                            } else {
                                cell.setCellStyle(numberStyle);
                            }
                        } else if ("DATE".equals(colMeta.getDataType())) {
                            if (val instanceof java.sql.Date) {
                                cell.setCellValue(((java.sql.Date) val).toLocalDate().toString());
                            } else {
                                cell.setCellValue(val.toString());
                            }
                            cell.setCellStyle(dateStyle);
                        } else {
                            cell.setCellValue(val.toString());
                            cell.setCellStyle(textStyle);
                        }
                    } catch (SQLException e) {
                        cell.setCellValue("");
                        cell.setCellStyle(textStyle);
                    }
                }
            });

            // Adjust Column Widths (max 50 chars)
            for (int i = 0; i < columns.size(); i++) {
                sheet.autoSizeColumn(i);
                int currentWidth = sheet.getColumnWidth(i);
                int maxWidth = 50 * 256;
                if (currentWidth > maxWidth) {
                    sheet.setColumnWidth(i, maxWidth);
                } else if (currentWidth < 14 * 256) {
                    sheet.setColumnWidth(i, 14 * 256);
                }
            }

            workbook.write(os);
            os.flush();
        } finally {
            workbook.dispose(); // Delete temporary XML disk files
            workbook.close();
        }
    }

    private void streamCsv(QueryCompiler.CompiledQuery compiled, OutputStream os) throws Exception {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
        // Emit UTF-8 BOM for Microsoft Excel compatibility
        writer.write('\ufeff');

        List<ColumnMetaDto> columns = compiled.getProjectedColumns();

        // Write Header
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) writer.write(',');
            writer.write(escapeCsv(columns.get(i).getLabel()));
        }
        writer.write("\r\n");

        // Stream Rows
        jdbcTemplate.query(compiled.getSelectSql(), compiled.getParameters(), rs -> {
            try {
                for (int i = 0; i < columns.size(); i++) {
                    if (i > 0) writer.write(',');
                    String sqlAlias = "col_" + i;
                    Object val = rs.getObject(sqlAlias);
                    writer.write(escapeCsv(val != null ? val.toString() : ""));
                }
                writer.write("\r\n");
            } catch (Exception e) {
                log.error("Error writing CSV row: {}", e.getMessage());
            }
        });

        writer.flush();
    }

    private String escapeCsv(String value) {
        if (value == null) return "\"\"";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return "\"" + value + "\"";
    }

    private Map<String, Object> mapResultSetRow(ResultSet rs, QueryCompiler.CompiledQuery compiled) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        List<ColumnMetaDto> cols = compiled.getProjectedColumns();
        for (int i = 0; i < cols.size(); i++) {
            String sqlAlias = "col_" + i;
            String fieldId = cols.get(i).getFieldId();
            Object val = rs.getObject(sqlAlias);
            if (val instanceof java.sql.Date) {
                row.put(fieldId, ((java.sql.Date) val).toLocalDate().toString());
            } else if (val instanceof java.sql.Timestamp) {
                row.put(fieldId, ((java.sql.Timestamp) val).toLocalDateTime().toString());
            } else {
                row.put(fieldId, val);
            }
        }
        return row;
    }

    private KpiSummaryDto computeKpiSummary(List<Map<String, Object>> rows, long totalMatching, List<ColumnMetaDto> columns) {
        BigDecimal sumNet = BigDecimal.ZERO;
        BigDecimal sumGross = BigDecimal.ZERO;
        Double totalLeaves = null;
        Double totalLop = null;

        boolean hasNet = false;
        boolean hasGross = false;

        for (Map<String, Object> r : rows) {
            Object netVal = r.get("payroll.netTakeHome");
            if (netVal instanceof Number) {
                sumNet = sumNet.add(BigDecimal.valueOf(((Number) netVal).doubleValue()));
                hasNet = true;
            }

            Object grossVal = r.get("payroll.totalGrossEarnings");
            if (grossVal instanceof Number) {
                sumGross = sumGross.add(BigDecimal.valueOf(((Number) grossVal).doubleValue()));
                hasGross = true;
            }

            Object leaveVal = r.get("leave.totalDays");
            if (leaveVal instanceof Number) {
                if (totalLeaves == null) totalLeaves = 0.0;
                totalLeaves += ((Number) leaveVal).doubleValue();
            }
        }

        return KpiSummaryDto.builder()
                .totalRecords(totalMatching)
                .sumNetTakeHome(hasNet ? sumNet : null)
                .sumGrossEarnings(hasGross ? sumGross : null)
                .totalLeaves(totalLeaves)
                .totalLopDays(totalLop)
                .build();
    }
}
