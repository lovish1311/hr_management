package com.example.hr_management_backend.features.tambola;

import com.example.hr_management_backend.features.tambola.service.TambolaTicketGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TambolaTicketGeneratorTest {

    private TambolaTicketGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new TambolaTicketGenerator();
    }

    @Test
    void testSingleTicketValidity() {
        TambolaTicketGenerator.GeneratedTicket ticket = generator.generateTicket();
        validateTicket(ticket);
    }

    @Test
    void testBatchTicketGeneration() {
        for (int i = 0; i < 500; i++) {
            TambolaTicketGenerator.GeneratedTicket ticket = generator.generateTicket();
            validateTicket(ticket);
        }
    }

    private void validateTicket(TambolaTicketGenerator.GeneratedTicket ticket) {
        int[][] grid = ticket.getGrid();
        assertEquals(3, grid.length, "Grid must have 3 rows");
        assertEquals(9, grid[0].length, "Grid must have 9 columns");

        assertEquals(5, ticket.getRow1Numbers().size(), "Row 1 must have exactly 5 numbers");
        assertEquals(5, ticket.getRow2Numbers().size(), "Row 2 must have exactly 5 numbers");
        assertEquals(5, ticket.getRow3Numbers().size(), "Row 3 must have exactly 5 numbers");
        assertEquals(15, ticket.getAllNumbers().size(), "Ticket must have exactly 15 numbers");

        // Verify uniqueness
        Set<Integer> unique = new HashSet<>(ticket.getAllNumbers());
        assertEquals(15, unique.size(), "All 15 numbers must be unique");

        // Verify column constraints and sorting
        for (int c = 0; c < 9; c++) {
            int countInCol = 0;
            int lastNum = -1;
            for (int r = 0; r < 3; r++) {
                int val = grid[r][c];
                if (val > 0) {
                    countInCol++;
                    // Check range
                    if (c == 0) {
                        assertTrue(val >= 1 && val <= 9, "Col 0 must be 1-9: got " + val);
                    } else if (c == 8) {
                        assertTrue(val >= 80 && val <= 90, "Col 8 must be 80-90: got " + val);
                    } else {
                        int min = c * 10;
                        int max = min + 9;
                        assertTrue(val >= min && val <= max, "Col " + c + " must be " + min + "-" + max + ": got " + val);
                    }

                    // Check ascending order in column
                    if (lastNum != -1) {
                        assertTrue(val > lastNum, "Column " + c + " numbers must be in ascending order: " + lastNum + " vs " + val);
                    }
                    lastNum = val;
                }
            }
            assertTrue(countInCol >= 1 && countInCol <= 3, "Column " + c + " must have 1, 2, or 3 numbers, got " + countInCol);
        }

        assertNotNull(ticket.getNumbersJson());
        assertFalse(ticket.getNumbersJson().isBlank());
        assertNotNull(ticket.getRow1());
        assertNotNull(ticket.getRow2());
        assertNotNull(ticket.getRow3());
    }
}
