package com.example.hr_management_backend;

import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ExcelParseTest {

    @Test
    public void testParseAllRowsRobust() throws Exception {
        File file = new File("C:/Users/Lovish/Downloads/15_july_beautified.xlsx");
        try (Workbook workbook = WorkbookFactory.create(new FileInputStream(file))) {
            Sheet sheet = workbook.getSheetAt(0);

            int parsedCount = 0;
            int presentCount = 0;

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                Cell nameCell = row.getCell(1);
                if (nameCell == null) continue;

                String rawName = nameCell.getStringCellValue().trim();
                List<LocalTime> times = new ArrayList<>();

                for (int c = 2; c < row.getLastCellNum(); c++) {
                    Cell cell = row.getCell(c);
                    if (cell != null) {
                        try {
                            String text = cell.getCellType() == CellType.STRING ? cell.getStringCellValue() : cell.toString();
                            LocalTime parsed = parseTime(text);
                            if (parsed != null) times.add(parsed);
                        } catch (Exception e) {
                            System.err.println("Error parsing cell at row " + r + " col " + c + " val=" + cell + ": " + e.getMessage());
                        }
                    }
                }

                parsedCount++;
                if (!times.isEmpty()) presentCount++;
                System.out.println("Row " + r + ": Name='" + rawName + "', FirstIn=" + (!times.isEmpty() ? times.get(0) : "NONE") + ", LastOut=" + (times.size() > 1 ? times.get(times.size() - 1) : "NONE") + ", Total Punches=" + times.size());
            }

            System.out.println("\nSUCCESS SUMMARY: Total Rows=" + parsedCount + ", Present=" + presentCount + ", Absent=" + (parsedCount - presentCount));
        }
    }

    private LocalTime parseTime(String text) {
        if (text == null) return null;
        text = text.replaceAll("\\s+", " ").trim().toUpperCase(Locale.ENGLISH);
        if (text.isBlank() || "-".equals(text) || text.contains("#")) return null;

        if (text.contains("AM") || text.contains("PM")) {
            DateTimeFormatter fmt = new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("[hh:mm:ss a][hh:mm a][h:m:s a][h:m a][hh:mma][h:ma]")
                    .toFormatter(Locale.ENGLISH);
            return LocalTime.parse(text, fmt);
        } else {
            DateTimeFormatter fmt = new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("[HH:mm:ss][HH:mm][H:m:s][H:m]")
                    .toFormatter(Locale.ENGLISH);
            return LocalTime.parse(text, fmt);
        }
    }
}
