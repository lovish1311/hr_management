package com.example.hr_management_backend.features.tambola.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TambolaTicketDto {
    private Long ticketId;
    private Long gameId;
    private Long employeeId;
    private List<List<Integer>> grid; // 3 rows, 9 columns
    private List<Integer> row1;
    private List<Integer> row2;
    private List<Integer> row3;
    private List<Integer> allNumbers;
}
