package com.example.hr_management_backend.features.querybuilder.service;

import com.example.hr_management_backend.features.querybuilder.dto.QueryRequestDto;
import com.example.hr_management_backend.features.querybuilder.dto.QueryTemplateDto;
import com.example.hr_management_backend.features.querybuilder.model.QueryTemplate;
import com.example.hr_management_backend.features.querybuilder.repository.QueryTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueryTemplateService {

    private final QueryTemplateRepository templateRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<QueryTemplateDto> getAccessibleTemplates(Long employeeId) {
        return templateRepository.findAccessibleTemplates(employeeId != null ? employeeId : -1L)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public QueryTemplateDto getTemplateById(Long id) {
        QueryTemplate entity = templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Query template not found: " + id));
        return mapToDto(entity);
    }

    @Transactional
    public QueryTemplateDto createTemplate(QueryTemplateDto dto, Long employeeId) {
        String jsonConfig = "";
        try {
            jsonConfig = objectMapper.writeValueAsString(dto.getQueryConfig());
        } catch (Exception e) {
            log.error("Failed to serialize query config: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid query configuration format");
        }

        QueryTemplate entity = QueryTemplate.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .category(dto.getCategory() != null ? dto.getCategory() : "Custom Reports")
                .isPublic(dto.getIsPublic() != null ? dto.getIsPublic() : true)
                .createdByEmployeeId(employeeId)
                .queryConfigJson(jsonConfig)
                .build();

        QueryTemplate saved = templateRepository.save(entity);
        return mapToDto(saved);
    }

    @Transactional
    public QueryTemplateDto updateTemplate(Long id, QueryTemplateDto dto, Long employeeId) {
        QueryTemplate entity = templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Query template not found: " + id));

        if (dto.getName() != null) entity.setName(dto.getName());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getCategory() != null) entity.setCategory(dto.getCategory());
        if (dto.getIsPublic() != null) entity.setIsPublic(dto.getIsPublic());

        if (dto.getQueryConfig() != null) {
            try {
                entity.setQueryConfigJson(objectMapper.writeValueAsString(dto.getQueryConfig()));
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid query configuration format");
            }
        }

        QueryTemplate updated = templateRepository.save(entity);
        return mapToDto(updated);
    }

    @Transactional
    public void deleteTemplate(Long id) {
        if (!templateRepository.existsById(id)) {
            throw new IllegalArgumentException("Query template not found: " + id);
        }
        templateRepository.deleteById(id);
    }

    private QueryTemplateDto mapToDto(QueryTemplate entity) {
        QueryRequestDto queryConfig = null;
        try {
            if (entity.getQueryConfigJson() != null && !entity.getQueryConfigJson().isBlank()) {
                queryConfig = objectMapper.readValue(entity.getQueryConfigJson(), QueryRequestDto.class);
            }
        } catch (Exception e) {
            log.warn("Failed to parse queryConfigJson for template {}: {}", entity.getId(), e.getMessage());
        }

        return QueryTemplateDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .category(entity.getCategory())
                .isPublic(entity.getIsPublic())
                .createdByEmployeeId(entity.getCreatedByEmployeeId())
                .queryConfig(queryConfig)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
