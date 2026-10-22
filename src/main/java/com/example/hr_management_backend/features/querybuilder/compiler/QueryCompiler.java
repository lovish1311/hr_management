package com.example.hr_management_backend.features.querybuilder.compiler;

import com.example.hr_management_backend.features.querybuilder.dto.*;
import com.example.hr_management_backend.features.querybuilder.service.QueryMetadataService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class QueryCompiler {

    private final QueryMetadataService metadataService;

    @Data
    @Builder
    public static class CompiledQuery {
        private String selectSql;
        private String countSql;
        private MapSqlParameterSource parameters;
        private List<ColumnMetaDto> projectedColumns;
        private Map<String, String> sqlAliasToFieldIdMap;
    }

    public CompiledQuery compile(QueryRequestDto request) {
        String rootEntity = request.getRootEntity() != null ? request.getRootEntity().toUpperCase() : "EMPLOYEE";
        MapSqlParameterSource params = new MapSqlParameterSource();
        AtomicInteger paramIdx = new AtomicInteger(0);

        // 1. Identify all referenced fields to resolve required table joins
        Set<String> referencedAliases = new HashSet<>();
        List<String> selectedFieldIds = request.getSelectedFields();
        if (selectedFieldIds == null || selectedFieldIds.isEmpty()) {
            selectedFieldIds = List.of("employee.code", "employee.fullName", "employee.department", "employee.status");
        }

        for (String fId : selectedFieldIds) {
            QueryMetadataService.FieldDefinition def = metadataService.getField(fId);
            if (def != null) {
                referencedAliases.add(def.getTableAlias());
            }
        }

        collectAliasesFromGroup(request.getFilterGroup(), referencedAliases);

        if (request.getSortBy() != null) {
            QueryMetadataService.FieldDefinition sortDef = metadataService.getField(request.getSortBy());
            if (sortDef != null) {
                referencedAliases.add(sortDef.getTableAlias());
            }
        }

        // 2. Build FROM and JOIN clauses
        StringBuilder fromBuilder = new StringBuilder();
        switch (rootEntity) {
            case "ATTENDANCE":
                fromBuilder.append("FROM attendance a JOIN employees e ON a.employee_id = e.id ");
                if (referencedAliases.contains("l")) fromBuilder.append("LEFT JOIN leave_requests l ON l.employee_id = e.id ");
                if (referencedAliases.contains("p")) fromBuilder.append("LEFT JOIN payroll_records p ON p.employee_id = e.id ");
                break;
            case "LEAVES":
                fromBuilder.append("FROM leave_requests l JOIN employees e ON l.employee_id = e.id ");
                if (referencedAliases.contains("a")) fromBuilder.append("LEFT JOIN attendance a ON a.employee_id = e.id ");
                if (referencedAliases.contains("p")) fromBuilder.append("LEFT JOIN payroll_records p ON p.employee_id = e.id ");
                break;
            case "PAYROLL":
                fromBuilder.append("FROM payroll_records p JOIN employees e ON p.employee_id = e.id ");
                if (referencedAliases.contains("a")) fromBuilder.append("LEFT JOIN attendance a ON a.employee_id = e.id ");
                if (referencedAliases.contains("l")) fromBuilder.append("LEFT JOIN leave_requests l ON l.employee_id = e.id ");
                break;
            case "EMPLOYEE":
            default:
                fromBuilder.append("FROM employees e ");
                if (referencedAliases.contains("a")) fromBuilder.append("LEFT JOIN attendance a ON a.employee_id = e.id ");
                if (referencedAliases.contains("l")) fromBuilder.append("LEFT JOIN leave_requests l ON l.employee_id = e.id ");
                if (referencedAliases.contains("p")) fromBuilder.append("LEFT JOIN payroll_records p ON p.employee_id = e.id ");
                break;
        }

        // 3. Projected Columns
        StringBuilder selectColsBuilder = new StringBuilder();
        List<ColumnMetaDto> projectedCols = new ArrayList<>();
        Map<String, String> sqlAliasToFieldIdMap = new HashMap<>();

        int colIdx = 0;
        for (String fId : selectedFieldIds) {
            QueryMetadataService.FieldDefinition def = metadataService.getField(fId);
            if (def == null) continue;

            String sqlAlias = "col_" + colIdx++;
            if (!selectColsBuilder.isEmpty()) {
                selectColsBuilder.append(", ");
            }
            selectColsBuilder.append(def.getSqlExpression()).append(" AS ").append(sqlAlias);

            String userAlias = (request.getColumnAliases() != null && request.getColumnAliases().containsKey(fId))
                    ? request.getColumnAliases().get(fId)
                    : def.getLabel();

            projectedCols.add(ColumnMetaDto.builder()
                    .fieldId(fId)
                    .label(userAlias)
                    .dataType(def.getDataType())
                    .build());

            sqlAliasToFieldIdMap.put(sqlAlias, fId);
        }

        if (selectColsBuilder.isEmpty()) {
            selectColsBuilder.append("e.id AS col_0");
            sqlAliasToFieldIdMap.put("col_0", "employee.id");
        }

        // 4. WHERE Clause compiled from AST FilterGroup
        String whereClause = compileFilterGroup(request.getFilterGroup(), params, paramIdx);
        String wherePart = (whereClause != null && !whereClause.isBlank()) ? "WHERE " + whereClause : "";

        // 5. ORDER BY Clause
        String sortPart = "ORDER BY e.id ASC";
        if (request.getSortBy() != null) {
            QueryMetadataService.FieldDefinition sortDef = metadataService.getField(request.getSortBy());
            if (sortDef != null) {
                String dir = "DESC".equalsIgnoreCase(request.getSortDirection()) ? "DESC" : "ASC";
                sortPart = "ORDER BY " + sortDef.getSqlExpression() + " " + dir + " NULLS LAST";
            }
        }

        // 6. Assemble Full Queries
        int page = (request.getPage() != null && request.getPage() >= 0) ? request.getPage() : 0;
        int rawPageSize = (request.getPageSize() != null && request.getPageSize() > 0) ? request.getPageSize() : 50;
        int pageSize = Math.min(rawPageSize, 500); // Production guard: Cap preview rows at 500 to prevent OOM

        String selectSql = String.format("SELECT %s %s %s %s LIMIT :limit OFFSET :offset",
                selectColsBuilder, fromBuilder, wherePart, sortPart);

        String countSql = String.format("SELECT COUNT(*) %s %s", fromBuilder, wherePart);

        params.addValue("limit", pageSize);
        params.addValue("offset", page * pageSize);

        return CompiledQuery.builder()
                .selectSql(selectSql)
                .countSql(countSql)
                .parameters(params)
                .projectedColumns(projectedCols)
                .sqlAliasToFieldIdMap(sqlAliasToFieldIdMap)
                .build();
    }

    private void collectAliasesFromGroup(FilterGroupDto group, Set<String> target) {
        if (group == null) return;
        if (group.getRules() != null) {
            for (FilterRuleDto rule : group.getRules()) {
                QueryMetadataService.FieldDefinition def = metadataService.getField(rule.getFieldId());
                if (def != null) {
                    target.add(def.getTableAlias());
                }
            }
        }
        if (group.getSubGroups() != null) {
            for (FilterGroupDto sub : group.getSubGroups()) {
                collectAliasesFromGroup(sub, target);
            }
        }
    }

    private String compileFilterGroup(FilterGroupDto group, MapSqlParameterSource params, AtomicInteger paramIdx) {
        if (group == null) return null;

        String conj = "OR".equalsIgnoreCase(group.getConjunction()) ? " OR " : " AND ";
        List<String> conditions = new ArrayList<>();

        if (group.getRules() != null) {
            for (FilterRuleDto rule : group.getRules()) {
                String cond = compileRule(rule, params, paramIdx);
                if (cond != null && !cond.isBlank()) {
                    conditions.add(cond);
                }
            }
        }

        if (group.getSubGroups() != null) {
            for (FilterGroupDto sub : group.getSubGroups()) {
                String subCond = compileFilterGroup(sub, params, paramIdx);
                if (subCond != null && !subCond.isBlank()) {
                    conditions.add("(" + subCond + ")");
                }
            }
        }

        if (conditions.isEmpty()) return null;
        return String.join(conj, conditions);
    }

    private String compileRule(FilterRuleDto rule, MapSqlParameterSource params, AtomicInteger paramIdx) {
        if (rule == null || rule.getFieldId() == null) return null;
        QueryMetadataService.FieldDefinition def = metadataService.getField(rule.getFieldId());
        if (def == null) return null;

        String expr = def.getSqlExpression();
        String op = rule.getOperator() != null ? rule.getOperator().toUpperCase() : "EQUALS";
        List<String> vals = rule.getValues() != null ? rule.getValues() : Collections.emptyList();

        switch (op) {
            case "IS_NULL":
                return expr + " IS NULL";
            case "IS_NOT_NULL":
                return expr + " IS NOT NULL";
            case "EQUALS": {
                if (vals.isEmpty() || vals.get(0).isBlank()) return null;
                String pName = "p_" + paramIdx.incrementAndGet();
                params.addValue(pName, bindTypedValue(vals.get(0), def.getDataType()));
                if ("STRING".equals(def.getDataType()) || "ENUM".equals(def.getDataType())) {
                    return "LOWER(" + expr + "::text) = LOWER(:" + pName + ")";
                }
                return expr + " = :" + pName;
            }
            case "NOT_EQUALS": {
                if (vals.isEmpty() || vals.get(0).isBlank()) return null;
                String pName = "p_" + paramIdx.incrementAndGet();
                params.addValue(pName, bindTypedValue(vals.get(0), def.getDataType()));
                if ("STRING".equals(def.getDataType()) || "ENUM".equals(def.getDataType())) {
                    return "(" + expr + " IS NULL OR LOWER(" + expr + "::text) != LOWER(:" + pName + "))";
                }
                return "(" + expr + " IS NULL OR " + expr + " != :" + pName + ")";
            }
            case "CONTAINS": {
                if (vals.isEmpty() || vals.get(0).isBlank()) return null;
                String pName = "p_" + paramIdx.incrementAndGet();
                params.addValue(pName, "%" + vals.get(0).trim() + "%");
                return expr + "::text ILIKE :" + pName;
            }
            case "STARTS_WITH": {
                if (vals.isEmpty() || vals.get(0).isBlank()) return null;
                String pName = "p_" + paramIdx.incrementAndGet();
                params.addValue(pName, vals.get(0).trim() + "%");
                return expr + "::text ILIKE :" + pName;
            }
            case "ENDS_WITH": {
                if (vals.isEmpty() || vals.get(0).isBlank()) return null;
                String pName = "p_" + paramIdx.incrementAndGet();
                params.addValue(pName, "%" + vals.get(0).trim());
                return expr + "::text ILIKE :" + pName;
            }
            case "GREATER_THAN":
            case "AFTER": {
                if (vals.isEmpty() || vals.get(0).isBlank()) return null;
                String pName = "p_" + paramIdx.incrementAndGet();
                params.addValue(pName, bindTypedValue(vals.get(0), def.getDataType()));
                return expr + " > :" + pName;
            }
            case "LESS_THAN":
            case "BEFORE": {
                if (vals.isEmpty() || vals.get(0).isBlank()) return null;
                String pName = "p_" + paramIdx.incrementAndGet();
                params.addValue(pName, bindTypedValue(vals.get(0), def.getDataType()));
                return expr + " < :" + pName;
            }
            case "BETWEEN": {
                if (vals.size() < 2 || vals.get(0).isBlank() || vals.get(1).isBlank()) return null;
                String pFrom = "p_" + paramIdx.incrementAndGet();
                String pTo = "p_" + paramIdx.incrementAndGet();
                params.addValue(pFrom, bindTypedValue(vals.get(0), def.getDataType()));
                params.addValue(pTo, bindTypedValue(vals.get(1), def.getDataType()));
                return expr + " BETWEEN :" + pFrom + " AND :" + pTo;
            }
            case "IN": {
                if (vals.isEmpty()) return null;
                String pName = "p_" + paramIdx.incrementAndGet();
                List<Object> typedList = new ArrayList<>();
                boolean isTextual = "STRING".equals(def.getDataType()) || "ENUM".equals(def.getDataType());
                for (String v : vals) {
                    if (!v.isBlank()) {
                        Object bound = bindTypedValue(v, def.getDataType());
                        if (isTextual && bound instanceof String) {
                            typedList.add(((String) bound).toLowerCase().trim());
                        } else {
                            typedList.add(bound);
                        }
                    }
                }
                if (typedList.isEmpty()) return null;
                params.addValue(pName, typedList);
                if (isTextual) {
                    return "LOWER(" + expr + "::text) IN (:" + pName + ")";
                }
                return expr + " IN (:" + pName + ")";
            }
            default:
                return null;
        }
    }

    private Object bindTypedValue(String raw, String dataType) {
        if (raw == null) return null;
        String val = raw.trim();
        try {
            switch (dataType) {
                case "NUMBER":
                    return Double.parseDouble(val);
                case "DATE":
                    return LocalDate.parse(val);
                default:
                    return val;
            }
        } catch (Exception e) {
            return val;
        }
    }
}
