package com.deliverypulse.upload;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

final class ImportTestFiles {

    private static final List<SheetSpec> PROJECTS = List.of(
            new SheetSpec("ECommerce", 8),
            new SheetSpec("CRM", 6),
            new SheetSpec("MobileApp", 6),
            new SheetSpec("DataPlatform", 5));

    private ImportTestFiles() {
    }

    static byte[] portfolioWorkbook() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int projectIndex = 0; projectIndex < PROJECTS.size(); projectIndex++) {
                SheetSpec spec = PROJECTS.get(projectIndex);
                var sheet = workbook.createSheet(spec.name());
                var header = sheet.createRow(0);
                String[] headers = projectIndex == 0
                        ? new String[] {" Task_ID ", "task name", "Team / Module", "Assignee", "Priority",
                                "Status", "Start_Date", "Due_Date", "Blocker / Dependency", "Description"}
                        : new String[] {"Task ID", "Task Name", "Team/Module", "Assignee", "Priority",
                                "Status", "Start Date", "Due Date", "Blocker / Dependency", "Description"};
                for (int column = 0; column < headers.length; column++) {
                    header.createCell(column).setCellValue(headers[column]);
                }
                CellStyle dateStyle = workbook.createCellStyle();
                dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
                for (int taskNumber = 1; taskNumber <= spec.taskCount(); taskNumber++) {
                    var row = sheet.createRow(taskNumber);
                    String taskId = spec.name() + "-" + String.format("%03d", taskNumber);
                    row.createCell(0).setCellValue(taskId);
                    row.createCell(1).setCellValue(spec.name() + " task " + taskNumber);
                    row.createCell(2).setCellValue("Core");
                    row.createCell(3).setCellValue("Team " + projectIndex);
                    row.createCell(4).setCellValue(taskNumber == 1 ? "Critical" : "Medium");
                    row.createCell(5).setCellValue(taskNumber == 1 ? "In Progress" : "Not Started");
                    var startDate = row.createCell(6);
                    startDate.setCellValue(java.sql.Date.valueOf(LocalDate.of(2026, 10, 1)));
                    startDate.setCellStyle(dateStyle);
                    var dueDate = row.createCell(7);
                    dueDate.setCellValue(java.sql.Date.valueOf(LocalDate.of(2026, 10, 31)));
                    dueDate.setCellStyle(dateStyle);
                    String blocker = taskNumber == 1 ? "External vendor approval"
                            : taskNumber == 2 ? spec.name() + "-001; waiting for vendor API" : "";
                    row.createCell(8).setCellValue(blocker);
                    row.createCell(9).setCellValue("Generated test-only task record");
                }
            }
            var readMe = workbook.createSheet("Read Me");
            readMe.createRow(0).createCell(0).setCellValue("Instructions, not project tasks");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    static byte[] csv(String rows) {
        return ("Task ID,Task Name,Team/Module,Assignee,Priority,Status,Start Date,Due Date,Blocker / Dependency,Description\n"
                + rows).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private record SheetSpec(String name, int taskCount) {
    }
}