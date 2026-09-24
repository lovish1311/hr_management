package com.example.hr_management_backend.features.tambola.dto;

import lombok.Data;

@Data
public class CreateTambolaGameDto {
    private String title;
    private Integer autoDrawIntervalSeconds; // null for manual draw, or >0 for auto
}
