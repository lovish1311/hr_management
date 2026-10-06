package com.example.hr_management_backend.features.scribbil.dto;

import com.example.hr_management_backend.features.scribbil.model.StrokeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrawStrokeDto {
    private String roomCode;
    private StrokeType strokeType; // DRAW, ERASE, CLEAR, UNDO, FILL
    private String color;
    private Double brushSize;
    private List<PointDto> points;
    private Long timestamp;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PointDto {
        private Double x;
        private Double y;
    }
}
