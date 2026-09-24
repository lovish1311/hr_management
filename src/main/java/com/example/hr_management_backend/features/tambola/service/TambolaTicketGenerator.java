package com.example.hr_management_backend.features.tambola.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class TambolaTicketGenerator {

    private final SecureRandom random = new SecureRandom();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Getter
    public static class GeneratedTicket {
        private final int[][] grid; // 3x9 grid
        private final List<Integer> row1Numbers;
        private final List<Integer> row2Numbers;
        private final List<Integer> row3Numbers;
        private final List<Integer> allNumbers;
        private final String numbersJson;
        private final String row1;
        private final String row2;
        private final String row3;
        private final String allNumbersStr;

        public GeneratedTicket(int[][] grid, ObjectMapper mapper) {
            this.grid = grid;
            this.row1Numbers = extractNumbers(grid[0]);
            this.row2Numbers = extractNumbers(grid[1]);
            this.row3Numbers = extractNumbers(grid[2]);

            List<Integer> all = new ArrayList<>();
            all.addAll(row1Numbers);
            all.addAll(row2Numbers);
            all.addAll(row3Numbers);
            all.sort(Integer::compareTo);
            this.allNumbers = Collections.unmodifiableList(all);

            this.row1 = this.row1Numbers.stream().map(String::valueOf).collect(Collectors.joining(","));
            this.row2 = this.row2Numbers.stream().map(String::valueOf).collect(Collectors.joining(","));
            this.row3 = this.row3Numbers.stream().map(String::valueOf).collect(Collectors.joining(","));
            this.allNumbersStr = this.allNumbers.stream().map(String::valueOf).collect(Collectors.joining(","));

            try {
                this.numbersJson = mapper.writeValueAsString(grid);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize ticket grid to JSON", e);
            }
        }

        private List<Integer> extractNumbers(int[] row) {
            List<Integer> list = new ArrayList<>();
            for (int val : row) {
                if (val > 0) {
                    list.add(val);
                }
            }
            return Collections.unmodifiableList(list);
        }
    }

    /**
     * Generates a single, strictly valid 3x9 Tambola ticket.
     * Rules:
     * 1. 3 rows and 9 columns.
     * 2. Exactly 15 numbers total.
     * 3. Exactly 5 numbers per row (4 blanks per row).
     * 4. Each column has 1, 2, or 3 numbers.
     * 5. Column ranges:
     *    Col 0: 1-9
     *    Col 1: 10-19
     *    Col 2: 20-29
     *    Col 3: 30-39
     *    Col 4: 40-49
     *    Col 5: 50-59
     *    Col 6: 60-69
     *    Col 7: 70-79
     *    Col 8: 80-90
     * 6. Numbers in each column are sorted strictly in ascending order from top row to bottom row.
     */
    public GeneratedTicket generateTicket() {
        boolean[][] mask = generateValidMask();
        int[][] grid = new int[3][9];

        for (int c = 0; c < 9; c++) {
            List<Integer> pool = getColumnPool(c);
            Collections.shuffle(pool, random);

            List<Integer> rowsWithNumber = new ArrayList<>();
            for (int r = 0; r < 3; r++) {
                if (mask[r][c]) {
                    rowsWithNumber.add(r);
                }
            }

            int count = rowsWithNumber.size();
            List<Integer> chosenNumbers = new ArrayList<>(pool.subList(0, count));
            Collections.sort(chosenNumbers);

            for (int i = 0; i < count; i++) {
                int r = rowsWithNumber.get(i);
                grid[r][c] = chosenNumbers.get(i);
            }
        }

        return new GeneratedTicket(grid, objectMapper);
    }

    /**
     * Generates a boolean mask 3x9 where:
     * - each row has exactly 5 true values
     * - each column has between 1 and 3 true values
     */
    private boolean[][] generateValidMask() {
        while (true) {
            boolean[][] mask = new boolean[3][9];

            for (int r = 0; r < 3; r++) {
                List<Integer> cols = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8));
                Collections.shuffle(cols, random);
                for (int i = 0; i < 5; i++) {
                    mask[r][cols.get(i)] = true;
                }
            }

            boolean valid = true;
            for (int c = 0; c < 9; c++) {
                int count = 0;
                for (int r = 0; r < 3; r++) {
                    if (mask[r][c]) count++;
                }
                if (count == 0 || count > 3) {
                    valid = false;
                    break;
                }
            }

            if (valid) {
                return mask;
            }
        }
    }

    private List<Integer> getColumnPool(int col) {
        int start;
        int end;
        if (col == 0) {
            start = 1;
            end = 9;
        } else if (col == 8) {
            start = 80;
            end = 90;
        } else {
            start = col * 10;
            end = start + 9;
        }

        List<Integer> pool = new ArrayList<>(end - start + 1);
        for (int i = start; i <= end; i++) {
            pool.add(i);
        }
        return pool;
    }
}
