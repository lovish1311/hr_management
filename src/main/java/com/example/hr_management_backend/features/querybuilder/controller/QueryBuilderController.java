package com.example.hr_management_backend.features.querybuilder.controller;

import com.example.hr_management_backend.features.employees.model.Employee;
import com.example.hr_management_backend.features.employees.repository.EmployeeRepository;
import com.example.hr_management_backend.features.querybuilder.dto.*;
import com.example.hr_management_backend.features.querybuilder.service.QueryExecutionService;
import com.example.hr_management_backend.features.querybuilder.service.QueryMetadataService;
import com.example.hr_management_backend.features.querybuilder.service.QueryTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/query-builder")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class QueryBuilderController {

    private final QueryMetadataService metadataService;
    private final QueryExecutionService executionService;
    private final QueryTemplateService templateService;
    private final EmployeeRepository employeeRepository;

    @GetMapping("/metadata")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'HR', 'MANAGER') or hasAuthority('PAYROLL_MANAGE')")
    public ResponseEntity<MetadataCatalogDto> getMetadataCatalog() {
        return ResponseEntity.ok(metadataService.getMetadataCatalog());
    }

    @PostMapping("/preview")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'HR', 'MANAGER') or hasAuthority('PAYROLL_MANAGE')")
    public ResponseEntity<QueryPreviewResponseDto> previewQuery(@RequestBody QueryRequestDto request) {
        return ResponseEntity.ok(executionService.previewQuery(request));
    }

    @PostMapping("/count")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'HR', 'MANAGER') or hasAuthority('PAYROLL_MANAGE')")
    public ResponseEntity<QueryCountResponseDto> countQuery(@RequestBody QueryRequestDto request) {
        return ResponseEntity.ok(executionService.countQuery(request));
    }

    @PostMapping("/export")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'HR', 'MANAGER') or hasAuthority('PAYROLL_MANAGE')")
    public ResponseEntity<byte[]> exportQuery(
            @RequestBody QueryRequestDto request,
            @RequestParam(defaultValue = "XLSX") String format) throws Exception {

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        boolean isCsv = "CSV".equalsIgnoreCase(format);
        String extension = isCsv ? "csv" : "xlsx";
        String filename = String.format("TeamJoy_Report_%s.%s", timestamp, extension);

        MediaType mediaType = isCsv
                ? MediaType.parseMediaType("text/csv; charset=UTF-8")
                : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        executionService.streamExport(request, format, baos);
        byte[] bytes = baos.toByteArray();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(mediaType)
                .contentLength(bytes.length)
                .body(bytes);
    }

    // --- Template Management Endpoints ---

    @GetMapping("/templates")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'HR', 'MANAGER') or hasAuthority('PAYROLL_MANAGE')")
    public ResponseEntity<List<QueryTemplateDto>> getTemplates(
            Authentication auth,
            @RequestHeader(value = "X-Employee-Id", required = false) Long headerEmpId) {
        Long empId = resolveEmployeeId(auth, headerEmpId);
        return ResponseEntity.ok(templateService.getAccessibleTemplates(empId));
    }

    @GetMapping("/templates/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'HR', 'MANAGER') or hasAuthority('PAYROLL_MANAGE')")
    public ResponseEntity<QueryTemplateDto> getTemplateById(@PathVariable Long id) {
        return ResponseEntity.ok(templateService.getTemplateById(id));
    }

    @PostMapping("/templates")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'HR', 'MANAGER') or hasAuthority('PAYROLL_MANAGE')")
    public ResponseEntity<QueryTemplateDto> createTemplate(
            @RequestBody QueryTemplateDto dto,
            Authentication auth,
            @RequestHeader(value = "X-Employee-Id", required = false) Long headerEmpId) {
        Long empId = resolveEmployeeId(auth, headerEmpId);
        return ResponseEntity.ok(templateService.createTemplate(dto, empId));
    }

    @PutMapping("/templates/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'HR', 'MANAGER') or hasAuthority('PAYROLL_MANAGE')")
    public ResponseEntity<QueryTemplateDto> updateTemplate(
            @PathVariable Long id,
            @RequestBody QueryTemplateDto dto,
            Authentication auth,
            @RequestHeader(value = "X-Employee-Id", required = false) Long headerEmpId) {
        Long empId = resolveEmployeeId(auth, headerEmpId);
        return ResponseEntity.ok(templateService.updateTemplate(id, dto, empId));
    }

    @DeleteMapping("/templates/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'HR', 'MANAGER') or hasAuthority('PAYROLL_MANAGE')")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }

    private Long resolveEmployeeId(Authentication auth, Long headerEmpId) {
        if (headerEmpId != null) return headerEmpId;
        if (auth != null && auth.getName() != null) {
            return employeeRepository.findByEmail(auth.getName())
                    .map(Employee::getId)
                    .orElse(null);
        }
        return null;
    }
}
