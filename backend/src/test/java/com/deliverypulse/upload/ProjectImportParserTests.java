package com.deliverypulse.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

class ProjectImportParserTests {

    private final ProjectImportParser parser = new ProjectImportParser();

    @Test
    void parsesAllProjectSheetsAndSkipsInstructions() throws Exception {
        ImportDocument document = parser.parseExcel(
                new ByteArrayInputStream(ImportTestFiles.portfolioWorkbook()), "portfolio.xlsx");

        assertThat(document.errors()).isEmpty();
        assertThat(document.projects()).hasSize(4);
        assertThat(document.projects()).extracting(project -> project.tasks().size())
                .containsExactly(8, 6, 6, 5);
        assertThat(document.projects().stream().mapToInt(project -> project.tasks().size()).sum()).isEqualTo(25);
        assertThat(document.skippedWorksheets()).containsExactly("Read Me");
        assertThat(document.projects()).allSatisfy(project -> assertThat(project.dependencies()).hasSize(1));
        assertThat(document.projects().getFirst().tasks().getFirst().startDate())
                .isEqualTo(java.time.LocalDate.of(2026, 10, 1));
        assertThat(document.projects().getFirst().tasks().get(1).blockerText())
                .isEqualTo("ECommerce-001; waiting for vendor API");
    }

        @Test
        void parsesLegacyXlsWorkbooks() throws Exception {
                byte[] bytes;
                try (var workbook = new HSSFWorkbook(); var output = new ByteArrayOutputStream()) {
                        var sheet = workbook.createSheet("LegacyProject");
                        var header = sheet.createRow(0);
                        header.createCell(0).setCellValue("Task ID");
                        header.createCell(1).setCellValue("Task Name");
                        header.createCell(2).setCellValue("Priority");
                        header.createCell(3).setCellValue("Status");
                        var row = sheet.createRow(1);
                        row.createCell(0).setCellValue("LEG-1");
                        row.createCell(1).setCellValue("Legacy import");
                        row.createCell(2).setCellValue("Low");
                        row.createCell(3).setCellValue("Done");
                        workbook.write(output);
                        bytes = output.toByteArray();
                }

                ImportDocument document = parser.parseExcel(new ByteArrayInputStream(bytes), "legacy.xls");
                assertThat(document.errors()).isEmpty();
                assertThat(document.projects()).singleElement().satisfies(project -> {
                        assertThat(project.name()).isEqualTo("LegacyProject");
                        assertThat(project.tasks()).hasSize(1);
                });
        }

    @Test
    void parsesDocumentedDateStringsAndLeavesDescriptiveBlockerAsText() throws Exception {
        String rows = "A-1,First,Core,Ada,High,In Progress,10/1/2026,01-Oct-2026,Waiting for vendor,External\n";
        ImportDocument document = parser.parseCsv(new ByteArrayInputStream(ImportTestFiles.csv(rows)), "Orders.csv");

        assertThat(document.errors()).isEmpty();
        assertThat(document.projects()).singleElement().satisfies(project -> {
            assertThat(project.name()).isEqualTo("Orders");
            assertThat(project.dependencies()).isEmpty();
            assertThat(project.tasks().getFirst().startDate()).isEqualTo(java.time.LocalDate.of(2026, 10, 1));
            assertThat(project.tasks().getFirst().dueDate()).isEqualTo(java.time.LocalDate.of(2026, 10, 1));
            assertThat(project.tasks().getFirst().blockerText()).isEqualTo("Waiting for vendor");
        });
    }

        @Test
        void resolvesOnlyKnownSemicolonSeparatedIdsAndPreservesOriginalBlockerText() throws Exception {
                String rows = "A-1,First,Core,Ada,High,Done,,,,\n"
                                + "A-2,Second,Core,Ada,High,Done,,,,\n"
                        + "B-1,Third,Core,Ada,High,In Progress,,,\" A-1; A-2; external system unavailable\",\n";
                ImportDocument document = parser.parseCsv(new ByteArrayInputStream(ImportTestFiles.csv(rows)), "Dependencies.csv");

                assertThat(document.errors()).isEmpty();
                assertThat(document.projects()).singleElement().satisfies(project -> {
                        assertThat(project.dependencies()).hasSize(2);
                        assertThat(project.tasks().get(2).blockerText())
                                        .isEqualTo(" A-1; A-2; external system unavailable");
                });
        }

    @Test
    void reportsMissingColumnsDuplicateIdsAndInvalidDates() throws Exception {
        ImportDocument missingColumns = parser.parseCsv(
                new ByteArrayInputStream("Task ID,Task Name\nA-1,Task\n".getBytes()), "Missing.csv");
        assertThat(missingColumns.errors()).extracting(ImportIssue::code)
                .contains("MISSING_REQUIRED_COLUMN");

        String rows = "A-1,First,Core,Ada,High,In Progress,not-a-date,,A-2,\n"
                + "A-1,Duplicate,Core,Ada,High,Done,,,,\n"
                + "A-2,Impossible date,Core,Ada,Medium,Done,2/30/2026,,,\n";
        ImportDocument invalidRows = parser.parseCsv(
                new ByteArrayInputStream(ImportTestFiles.csv(rows)), "Duplicate.csv");
        assertThat(invalidRows.errors()).extracting(ImportIssue::code)
                .contains("INVALID_DATE", "DUPLICATE_TASK_ID");

        ImportDocument invalidStatus = parser.parseCsv(new ByteArrayInputStream(ImportTestFiles.csv(
                "A-3,Invalid status,Core,Ada,High,Unknown,,,,\n")), "Status.csv");
        assertThat(invalidStatus.errors()).extracting(ImportIssue::code).contains("INVALID_STATUS");
    }

        @Test
        void reportsRowsWithOnlyOptionalFieldsInsteadOfSilentlySkippingThem() throws Exception {
                String rows = ",,,,,,2026-10-15,,,\n";
                ImportDocument document = parser.parseCsv(new ByteArrayInputStream(ImportTestFiles.csv(rows)), "Partial.csv");

                assertThat(document.errors()).extracting(ImportIssue::code)
                                .contains("MISSING_TASK_ID", "MISSING_TASK_NAME", "MISSING_PRIORITY", "MISSING_STATUS");
        }

        @Test
        void rejectsCsvWithHeadersButNoTaskRows() throws Exception {
                String headerOnly = "Task ID,Task Name,Priority,Status\n";
                ImportDocument document = parser.parseCsv(new ByteArrayInputStream(
                                headerOnly.getBytes(java.nio.charset.StandardCharsets.UTF_8)), "EmptyProject.csv");

                assertThat(document.errors()).extracting(ImportIssue::code).containsExactly("NO_TASK_ROWS");
        }

    @Test
    void rejectsMalformedWorkbook() {
        assertThatThrownBy(() -> parser.parseExcel(
                new ByteArrayInputStream("not an Excel file".getBytes()), "broken.xlsx"))
                .isInstanceOf(UploadRejectedException.class);

        assertThatThrownBy(() -> parser.parseCsv(new ByteArrayInputStream(ImportTestFiles.csv(
                "A-1,Task,Core,Ada,High,Done,,,,,unexpected-extra-field\n")), "broken.csv"))
                .isInstanceOf(UploadRejectedException.class)
                .hasMessageContaining("more fields than the header");
    }

        @Test
        void rejectsMalformedUtf8CsvInsteadOfReplacingInvalidBytes() {
                byte[] header = "Task ID,Task Name,Priority,Status\nA-1,".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                byte[] tail = ",High,Done\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                byte[] malformed = new byte[header.length + 2 + tail.length];
                System.arraycopy(header, 0, malformed, 0, header.length);
                malformed[header.length] = (byte) 0xC3;
                malformed[header.length + 1] = (byte) 0x28;
                System.arraycopy(tail, 0, malformed, header.length + 2, tail.length);

                assertThatThrownBy(() -> parser.parseCsv(new ByteArrayInputStream(malformed), "InvalidUtf8.csv"))
                                .isInstanceOf(UploadRejectedException.class);
        }

        @Test
        void reportsEmptyWorksheetsWithoutCrashing() throws Exception {
                byte[] bytes;
                try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
                        workbook.createSheet("Empty");
                        workbook.createSheet("Instructions").createRow(0).createCell(0).setCellValue("How to use this file");
                        workbook.write(output);
                        bytes = output.toByteArray();
                }

                ImportDocument document = parser.parseExcel(new ByteArrayInputStream(bytes), "empty.xlsx");
                assertThat(document.skippedWorksheets()).contains("Empty", "Instructions");
                assertThat(document.warnings()).extracting(ImportIssue::code).contains("EMPTY_WORKSHEET");
                assertThat(document.errors()).extracting(ImportIssue::code).contains("NO_PROJECT_SHEETS");
        }

    @Test
    void collapsesRepeatedDependencyReferencesIntoOneRecord() throws Exception {
        String rows = "A-1,First,Core,Ada,High,Done,,,,\n"
                + "B-1,Second,Core,Ada,High,In Progress,,,A-1; a-1 ;A-1,\n";
        ImportDocument document = parser.parseCsv(new ByteArrayInputStream(ImportTestFiles.csv(rows)), "Repeats.csv");

        assertThat(document.errors()).isEmpty();
        assertThat(document.projects().getFirst().dependencies()).singleElement()
                .satisfies(dependency -> assertThat(dependency.prerequisiteTaskKey()).isEqualTo("A-1"));
    }

    @Test
    void treatsLineBreaksInBlockerCellsAsSeparatorsLikeTheRiskRules() throws Exception {
        String rows = "A-1,First,Core,Ada,High,Done,,,,\n"
                + "A-2,Second,Core,Ada,High,Done,,,,\n"
                + "B-1,Third,Core,Ada,High,In Progress,,,\"A-1\nA-2\",\n";
        ImportDocument document = parser.parseCsv(new ByteArrayInputStream(ImportTestFiles.csv(rows)), "Lines.csv");

        assertThat(document.errors()).isEmpty();
        assertThat(document.projects().getFirst().dependencies())
                .extracting(ImportedDependency::prerequisiteTaskKey).containsExactly("A-1", "A-2");
    }

    @Test
    void treatsCommasAsDependencySeparators() throws Exception {
        String rows = "A-1,First,Core,Ada,High,Done,,,,\n"
                + "A-2,Second,Core,Ada,High,Done,,,,\n"
                + "B-1,Third,Core,Ada,High,In Progress,,,\"A-1, A-2\",\n";
        ImportDocument document = parser.parseCsv(new ByteArrayInputStream(ImportTestFiles.csv(rows)), "Commas.csv");

        assertThat(document.errors()).isEmpty();
        assertThat(document.projects().getFirst().dependencies())
                .extracting(ImportedDependency::prerequisiteTaskKey).containsExactly("A-1", "A-2");
    }

    @Test
    void readsCachedFormulaResultsInsteadOfFormulaText() throws Exception {
        byte[] bytes;
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Formulas");
            var header = sheet.createRow(0);
            String[] headers = {"Task ID", "Task Name", "Priority", "Status", "Due Date"};
            for (int column = 0; column < headers.length; column++) {
                header.createCell(column).setCellValue(headers[column]);
            }
            var dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
            var row = sheet.createRow(1);
            row.createCell(0).setCellFormula("\"F-\"&ROW()");
            row.createCell(1).setCellValue("Formula-driven task");
            row.createCell(2).setCellValue("High");
            row.createCell(3).setCellFormula("\"In Progress\"");
            var due = row.createCell(4);
            due.setCellFormula("DATE(2026,10,20)");
            due.setCellStyle(dateStyle);
            // Excel stores the computed result with each formula; simulate a saved workbook.
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            workbook.write(output);
            bytes = output.toByteArray();
        }

        ImportDocument document = parser.parseExcel(new ByteArrayInputStream(bytes), "formulas.xlsx");

        assertThat(document.errors()).isEmpty();
        assertThat(document.projects().getFirst().tasks()).singleElement().satisfies(task -> {
            assertThat(task.taskKey()).isEqualTo("F-2");
            assertThat(task.status()).isEqualTo(com.deliverypulse.shared.domain.TaskStatus.IN_PROGRESS);
            assertThat(task.dueDate()).isEqualTo(java.time.LocalDate.of(2026, 10, 20));
        });
    }

    @Test
    void skipsHiddenHelperSheetsAndTrimsSheetNames() throws Exception {
        byte[] bytes;
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet(" Payments ");
            var header = sheet.createRow(0);
            String[] headers = {"Task ID", "Task Name", "Priority", "Status"};
            for (int column = 0; column < headers.length; column++) {
                header.createCell(column).setCellValue(headers[column]);
            }
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("P-1");
            row.createCell(1).setCellValue("Refunds");
            row.createCell(2).setCellValue("Low");
            row.createCell(3).setCellValue("Done");
            var lists = workbook.createSheet("Lists");
            lists.createRow(0).createCell(0).setCellValue("Priority values");
            workbook.setSheetHidden(workbook.getSheetIndex(lists), true);
            workbook.write(output);
            bytes = output.toByteArray();
        }

        ImportDocument document = parser.parseExcel(new ByteArrayInputStream(bytes), "hidden.xlsx");

        assertThat(document.errors()).isEmpty();
        assertThat(document.projects()).extracting(ImportProject::name).containsExactly("Payments");
        assertThat(document.skippedWorksheets()).containsExactly("Lists");
        assertThat(document.warnings()).extracting(ImportIssue::code).containsExactly("HIDDEN_WORKSHEET");
    }

    @Test
    void acceptsCsvWithTrailingEmptyHeaderColumn() throws Exception {
        String csv = "Task ID,Task Name,Priority,Status,\nA-1,Task,High,Done,\n";
        ImportDocument document = parser.parseCsv(new ByteArrayInputStream(
                csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)), "Trailing.csv");

        assertThat(document.errors()).isEmpty();
        assertThat(document.projects().getFirst().tasks()).hasSize(1);
    }
}