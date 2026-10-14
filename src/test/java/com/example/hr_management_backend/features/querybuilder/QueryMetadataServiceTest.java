package com.example.hr_management_backend.features.querybuilder;

import com.example.hr_management_backend.features.querybuilder.dto.MetadataCatalogDto;
import com.example.hr_management_backend.features.querybuilder.service.QueryMetadataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QueryMetadataServiceTest {

    private QueryMetadataService metadataService;

    @BeforeEach
    void setUp() {
        metadataService = new QueryMetadataService();
    }

    @Test
    @DisplayName("Should expose all 4 core entities in metadata catalog")
    void testCatalogEntities() {
        MetadataCatalogDto catalog = metadataService.getMetadataCatalog();

        assertNotNull(catalog);
        assertNotNull(catalog.getEntities());
        assertEquals(4, catalog.getEntities().size());

        assertTrue(catalog.getEntities().stream().anyMatch(e -> "EMPLOYEE".equals(e.getEntityId())));
        assertTrue(catalog.getEntities().stream().anyMatch(e -> "ATTENDANCE".equals(e.getEntityId())));
        assertTrue(catalog.getEntities().stream().anyMatch(e -> "LEAVES".equals(e.getEntityId())));
        assertTrue(catalog.getEntities().stream().anyMatch(e -> "PAYROLL".equals(e.getEntityId())));
    }

    @Test
    @DisplayName("Should correctly register and validate fields")
    void testFieldRegistry() {
        assertTrue(metadataService.isFieldRegistered("employee.code"));
        assertTrue(metadataService.isFieldRegistered("payroll.netTakeHome"));
        assertTrue(metadataService.isFieldRegistered("attendance.date"));
        assertTrue(metadataService.isFieldRegistered("leave.totalDays"));
        assertFalse(metadataService.isFieldRegistered("non_existent_field"));

        QueryMetadataService.FieldDefinition def = metadataService.getField("employee.department");
        assertNotNull(def);
        assertEquals("ENUM", def.getDataType());
        assertTrue(def.getOptions().contains("Engineering"));
        assertTrue(def.getOptions().contains("HR"));
    }
}
