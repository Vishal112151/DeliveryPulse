package com.deliverypulse.upload;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import com.deliverypulse.shared.domain.IssueType;
import com.deliverypulse.shared.domain.Priority;
import com.deliverypulse.shared.domain.TaskStatus;

@Component
public class ProjectImportParser {

    private static final Set<Column> REQUIRED_COLUMNS = Set.of(
            Column.TASK_ID, Column.TASK_NAME, Column.PRIORITY, Column.STATUS);
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            strictDateFormat("M/d/uuuu", Locale.US),
            strictDateFormat("M/d/uu", Locale.US),
            strictDateFormat("d-MMM-uuuu", Locale.ENGLISH),
            strictDateFormat("uuuu/M/d", Locale.ROOT));
    private static final Pattern TASK_ID_LIKE = Pattern.compile("(?i)^[a-z][a-z0-9_]*-\\d+$");

    ImportDocument parseExcel(InputStream input, String fileName) throws IOException {
        List<ImportProject> projects = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<ImportIssue> warnings = new ArrayList<>();
        List<ImportIssue> errors = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(input)) {
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            // Read the result Excel saved with each formula, not the formula text. Nothing is recalculated.
            formatter.setUseCachedValuesForFormulaCells(true);
            for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
                Sheet sheet = workbook.getSheetAt(index);
                String sheetName = sheet.getSheetName().trim();
                if (isInstructionSheet(sheetName)) {
                    skipped.add(sheetName);
                    continue;
                }
                if (workbook.isSheetHidden(index) || workbook.isSheetVeryHidden(index)) {
                    skipped.add(sheetName);
                    warnings.add(issue(sheetName, null, null, "HIDDEN_WORKSHEET", "Hidden worksheet was skipped."));
                    continue;
                }
                TabularSheet table = toTable(sheet, formatter);
                if (table.rows().isEmpty() && table.header().stream().allMatch(String::isBlank)) {
                    skipped.add(sheetName);
                    warnings.add(issue(sheetName, null, null, "EMPTY_WORKSHEET", "Empty worksheet was skipped."));
                    continue;
                }
                parseProject(table, sheetName, projects, warnings, errors);
            }
        } catch (EncryptedDocumentException exception) {
            throw new UploadRejectedException("Encrypted Excel workbooks are not supported.");
        } catch (IOException exception) {
            throw new UploadRejectedException("The Excel file is malformed or uses an unsupported workbook format.");
        } catch (RuntimeException exception) {
            if (exception instanceof UploadRejectedException rejected) {
                throw rejected;
            }
            throw new UploadRejectedException("The Excel file is malformed or uses an unsupported workbook format.");
        }

        addDuplicateProjectNameErrors(projects, errors);
        if (projects.isEmpty() && errors.isEmpty()) {
            errors.add(issue(fileName, null, null, "NO_PROJECT_SHEETS", "No project worksheets with task data were found."));
        }
        return new ImportDocument(projects, skipped, warnings, errors);
    }

    ImportDocument parseCsv(InputStream input, String fileName) throws IOException {
        List<ImportProject> projects = new ArrayList<>();
        List<ImportIssue> warnings = new ArrayList<>();
        List<ImportIssue> errors = new ArrayList<>();
        String projectName = projectNameFromFile(fileName);
        if (projectName.isBlank()) {
            errors.add(issue(fileName, 1, null, "INVALID_PROJECT_NAME", "CSV filename must provide a project name."));
            return new ImportDocument(projects, List.of(), warnings, errors);
        }

        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                // Spreadsheet exports often end the header with an empty column; mapHeaders ignores it.
                .setAllowMissingColumnNames(true)
                .get()
                .parse(new InputStreamReader(input, StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)))) {
            List<String> headers = new ArrayList<>(parser.getHeaderNames());
            List<TabularRow> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                if (record.size() > headers.size()) {
                    throw new UploadRejectedException("CSV row " + (record.getRecordNumber() + 1)
                            + " contains more fields than the header.");
                }
                List<CellValue> values = new ArrayList<>(headers.size());
                for (int index = 0; index < headers.size(); index++) {
                    values.add(CellValue.text(index < record.size() ? record.get(index) : ""));
                }
                rows.add(new TabularRow(Math.toIntExact(record.getRecordNumber() + 1), values));
            }
            parseProject(new TabularSheet(projectName, headers, rows, 1), projectName,
                    projects, warnings, errors);
        } catch (IllegalArgumentException exception) {
            throw new UploadRejectedException("The CSV file is malformed or has inconsistent columns.");
        } catch (IOException exception) {
            throw new UploadRejectedException("The CSV file is malformed or could not be parsed.");
        }

        addDuplicateProjectNameErrors(projects, errors);
        if (projects.isEmpty() && errors.isEmpty()) {
            errors.add(issue(projectName, null, null, "NO_TASK_ROWS", "CSV contains no importable task rows."));
        }
        return new ImportDocument(projects, List.of(), warnings, errors);
    }

    private TabularSheet toTable(Sheet sheet, DataFormatter formatter) {
        int headerIndex = findHeaderRow(sheet, formatter);
        Row headerRow = sheet.getRow(headerIndex);
        if (headerRow == null) {
            return new TabularSheet(sheet.getSheetName(), List.of(), List.of(), headerIndex + 1);
        }
        int width = Math.max(0, headerRow.getLastCellNum());
        List<String> headers = new ArrayList<>(width);
        for (int column = 0; column < width; column++) {
            headers.add(formatter.formatCellValue(headerRow.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)));
        }
        List<TabularRow> rows = new ArrayList<>();
        for (int rowIndex = headerIndex + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            List<CellValue> values = new ArrayList<>(width);
            boolean blank = true;
            for (int column = 0; column < width; column++) {
                Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                CellValue value = cellValue(cell, formatter);
                values.add(value);
                blank &= value.text().isBlank();
            }
            if (!blank) {
                rows.add(new TabularRow(rowIndex + 1, values));
            }
        }
        return new TabularSheet(sheet.getSheetName(), headers, rows, headerIndex + 1);
    }

    private int findHeaderRow(Sheet sheet, DataFormatter formatter) {
        int lastRow = Math.min(sheet.getLastRowNum(), 9);
        for (int rowIndex = 0; rowIndex <= lastRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            boolean hasTaskId = false;
            boolean hasTaskName = false;
            for (Cell cell : row) {
                String normalized = normalizeHeader(formatter.formatCellValue(cell));
                hasTaskId |= normalized.equals("taskid") || normalized.equals("taskkey");
                hasTaskName |= normalized.equals("taskname");
            }
            if (hasTaskId && hasTaskName) {
                return rowIndex;
            }
        }
        return 0;
    }

    private CellValue cellValue(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return CellValue.text("");
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            try {
                return new CellValue(formatter.formatCellValue(cell), cell.getLocalDateTimeCellValue().toLocalDate());
            } catch (RuntimeException exception) {
                return CellValue.text(formatter.formatCellValue(cell));
            }
        }
        return CellValue.text(formatter.formatCellValue(cell));
    }

    private void parseProject(TabularSheet table, String projectName,
            List<ImportProject> projects, List<ImportIssue> warnings, List<ImportIssue> errors) {
        if (projectName.length() > 160) {
            errors.add(issue(table.name(), null, null, "PROJECT_NAME_TOO_LONG", "Project name exceeds 160 characters."));
            return;
        }
        Map<Column, Integer> columns = mapHeaders(table, warnings, errors);
        boolean missingRequired = REQUIRED_COLUMNS.stream().anyMatch(column -> !columns.containsKey(column));
        if (missingRequired) {
            for (Column column : REQUIRED_COLUMNS) {
                if (!columns.containsKey(column)) {
                    errors.add(issue(table.name(), table.headerRow(), column.header,
                            "MISSING_REQUIRED_COLUMN", "Required column '" + column.header + "' is missing."));
                }
            }
            return;
        }

        List<ImportedTask> tasks = new ArrayList<>();
        Map<String, ImportedTask> byKey = new LinkedHashMap<>();
        Set<String> seenKeys = new HashSet<>();
        Set<String> duplicateKeys = new HashSet<>();
        Map<String, String> rawBlockers = new HashMap<>();
        for (TabularRow row : table.rows()) {
            String taskKey = value(row, columns, Column.TASK_ID).trim();
            String taskName = value(row, columns, Column.TASK_NAME).trim();
            String priorityText = value(row, columns, Column.PRIORITY).trim();
            String statusText = value(row, columns, Column.STATUS).trim();
            if (row.values().stream().allMatch(value -> value.text().isBlank())) {
                continue;
            }
            boolean valid = true;
            String normalizedKey = normalizeTaskId(taskKey);
            if (!taskKey.isBlank() && !seenKeys.add(normalizedKey)) {
                duplicateKeys.add(normalizedKey);
                errors.add(issue(table.name(), row.number(), "Task ID", "DUPLICATE_TASK_ID",
                        "Task ID '" + taskKey + "' is duplicated within this project."));
                valid = false;
            }
            valid &= required(taskKey, table.name(), row.number(), "Task ID", "MISSING_TASK_ID", errors);
            valid &= required(taskName, table.name(), row.number(), "Task Name", "MISSING_TASK_NAME", errors);
            valid &= required(priorityText, table.name(), row.number(), "Priority", "MISSING_PRIORITY", errors);
            valid &= required(statusText, table.name(), row.number(), "Status", "MISSING_STATUS", errors);
            if (taskKey.length() > 100) {
                errors.add(issue(table.name(), row.number(), "Task ID", "TASK_ID_TOO_LONG", "Task ID exceeds 100 characters."));
                valid = false;
            }
            if (taskName.length() > 300) {
                errors.add(issue(table.name(), row.number(), "Task Name", "TASK_NAME_TOO_LONG", "Task name exceeds 300 characters."));
                valid = false;
            }
            String teamOrModule = value(row, columns, Column.TEAM_MODULE).trim();
            String assignee = value(row, columns, Column.ASSIGNEE).trim();
            if (teamOrModule.length() > 160) {
                errors.add(issue(table.name(), row.number(), "Team/Module", "FIELD_TOO_LONG", "Team/Module exceeds 160 characters."));
                valid = false;
            }
            if (assignee.length() > 160) {
                errors.add(issue(table.name(), row.number(), "Assignee", "FIELD_TOO_LONG", "Assignee exceeds 160 characters."));
                valid = false;
            }
            Priority priority = parsePriority(priorityText);
            if (priority == null && !priorityText.isBlank()) {
                errors.add(issue(table.name(), row.number(), "Priority", "INVALID_PRIORITY", "Priority must be Low, Medium, High, or Critical."));
                valid = false;
            }
            TaskStatus status = parseStatus(statusText);
            if (status == null && !statusText.isBlank()) {
                errors.add(issue(table.name(), row.number(), "Status", "INVALID_STATUS", "Status must be Not Started, In Progress, Blocked, or Done."));
                valid = false;
            }
            LocalDate startDate = parseDate(valueCell(row, columns, Column.START_DATE), table.name(), row.number(), "Start Date", errors);
            LocalDate dueDate = parseDate(valueCell(row, columns, Column.DUE_DATE), table.name(), row.number(), "Due Date", errors);
            LocalDate milestoneDate = parseDate(valueCell(row, columns, Column.MILESTONE_DATE), table.name(), row.number(),
                    "Milestone Date", errors);
            if (dateHasError(errors, table.name(), row.number(), "Start Date")
                    || dateHasError(errors, table.name(), row.number(), "Due Date")
                    || dateHasError(errors, table.name(), row.number(), "Milestone Date")) {
                valid = false;
            }
            String typeText = value(row, columns, Column.TYPE).trim();
            IssueType issueType = typeText.isBlank() ? IssueType.TASK : parseIssueType(typeText);
            if (issueType == null) {
                errors.add(issue(table.name(), row.number(), "Type", "INVALID_TYPE", "Type must be Task, Story, Bug, or Defect."));
                valid = false;
            }
            String milestone = value(row, columns, Column.MILESTONE).trim();
            if (milestone.length() > 160) {
                errors.add(issue(table.name(), row.number(), "Milestone", "FIELD_TOO_LONG", "Milestone exceeds 160 characters."));
                valid = false;
            }
            if (milestone.isBlank() && milestoneDate != null) {
                errors.add(issue(table.name(), row.number(), "Milestone Date", "MILESTONE_DATE_WITHOUT_MILESTONE",
                        "Milestone Date needs a Milestone name on the same row."));
                valid = false;
            }
            if (!valid) {
                continue;
            }
            String blockerText = value(row, columns, Column.BLOCKER_DEPENDENCY);
            ImportedTask task = new ImportedTask(row.number(), taskKey, taskName,
                    optional(teamOrModule), optional(assignee),
                    priority, status, startDate, dueDate, preserveOptional(blockerText),
                    optional(value(row, columns, Column.DESCRIPTION)), issueType, optional(milestone), milestoneDate,
                    typeText.isBlank() ? null : typeText.substring(0, Math.min(typeText.length(), 40)));
            tasks.add(task);
            byKey.put(normalizedKey, task);
            rawBlockers.put(normalizedKey, blockerText);
        }

        List<ImportedDependency> dependencies = new ArrayList<>();
        for (ImportedTask task : tasks) {
            String blockerText = rawBlockers.get(normalizeTaskId(task.taskKey()));
            if (blockerText == null || blockerText.isBlank()) {
                continue;
            }
            Set<String> linked = new HashSet<>();
            // Same separators as the risk rules (RuleSupport.blockerTokens): semicolons, commas, line breaks.
            for (String token : blockerText.split("[;,\\r\\n]")) {
                String candidate = token.trim();
                if (candidate.isBlank()) {
                    continue;
                }
                String normalized = normalizeTaskId(candidate);
                if (byKey.containsKey(normalized) && !duplicateKeys.contains(normalized)) {
                    if (normalized.equals(normalizeTaskId(task.taskKey()))) {
                        errors.add(issue(table.name(), task.row(), "Blocker / Dependency", "SELF_DEPENDENCY",
                                "A task cannot depend on itself."));
                    } else if (linked.add(normalized)) {
                        dependencies.add(new ImportedDependency(task.taskKey(), byKey.get(normalized).taskKey(), candidate));
                    }
                } else if (TASK_ID_LIKE.matcher(candidate).matches()) {
                    warnings.add(issue(table.name(), task.row(), "Blocker / Dependency", "UNRESOLVED_TASK_REFERENCE",
                            "Reference '" + candidate + "' did not match a task ID in this project and was kept as blocker text."));
                }
            }
        }
        tasks = withMilestoneDates(tasks, table.name(), errors);
        if (!tasks.isEmpty()) {
            projects.add(new ImportProject(projectName, tasks, dependencies));
        } else if (table.rows().isEmpty()) {
            warnings.add(issue(table.name(), null, null, "EMPTY_PROJECT_WORKSHEET", "Worksheet has headers but no task rows."));
        }
    }

    private Map<Column, Integer> mapHeaders(TabularSheet table, List<ImportIssue> warnings, List<ImportIssue> errors) {
        Map<Column, Integer> columns = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < table.header().size(); index++) {
            String original = table.header().get(index);
            String normalized = normalizeHeader(original);
            if (normalized.isBlank()) {
                continue;
            }
            if (!seen.add(normalized)) {
                errors.add(issue(table.name(), table.headerRow(), original, "DUPLICATE_COLUMN",
                        "Header is duplicated after normalization."));
                continue;
            }
            Column column = Column.fromNormalized(normalized);
            if (column == null) {
                warnings.add(issue(table.name(), table.headerRow(), original, "UNKNOWN_COLUMN", "Unrecognized column was ignored."));
            } else if (columns.putIfAbsent(column, index) != null) {
                errors.add(issue(table.name(), table.headerRow(), original, "DUPLICATE_COLUMN",
                        "Multiple headers map to '" + column.header + "'."));
            }
        }
        return columns;
    }

    /**
     * A milestone's date may be given on any one of its rows. Every row of that milestone gets the date;
     * two different dates for the same milestone is an error.
     */
    private List<ImportedTask> withMilestoneDates(List<ImportedTask> tasks, String sheet, List<ImportIssue> errors) {
        Map<String, LocalDate> dates = new HashMap<>();
        for (ImportedTask task : tasks) {
            if (task.milestone() == null || task.milestoneDate() == null) {
                continue;
            }
            String key = task.milestone().toLowerCase(Locale.ROOT);
            LocalDate known = dates.putIfAbsent(key, task.milestoneDate());
            if (known != null && !known.equals(task.milestoneDate())) {
                errors.add(issue(sheet, task.row(), "Milestone Date", "CONFLICTING_MILESTONE_DATE",
                        "Milestone '" + task.milestone() + "' already has date " + known + " on another row."));
            }
        }
        return tasks.stream().map(task -> task.milestone() == null ? task : new ImportedTask(task.row(),
                task.taskKey(), task.taskName(), task.teamOrModule(), task.assignee(), task.priority(), task.status(),
                task.startDate(), task.dueDate(), task.blockerText(), task.description(), task.issueType(),
                task.milestone(), dates.get(task.milestone().toLowerCase(Locale.ROOT)), task.workType())).toList();
    }

    private LocalDate parseDate(CellValue cell, String sheet, int row, String column, List<ImportIssue> errors) {
        if (cell == null || cell.text().isBlank()) {
            return null;
        }
        if (cell.dateValue() != null) {
            return cell.dateValue();
        }
        String value = cell.text().trim();
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, format);
            } catch (DateTimeParseException ignored) {
                // Try the documented formats in order.
            }
        }
        errors.add(issue(sheet, row, column, "INVALID_DATE",
                "Date must use yyyy-MM-dd, M/d/yyyy, M/d/yy, d-MMM-yyyy, or yyyy/M/d."));
        return null;
    }

    private static DateTimeFormatter strictDateFormat(String pattern, Locale locale) {
        return DateTimeFormatter.ofPattern(pattern, locale).withResolverStyle(ResolverStyle.STRICT);
    }

    private boolean dateHasError(List<ImportIssue> errors, String sheet, int row, String column) {
        return errors.stream().anyMatch(error -> error.worksheet().equals(sheet)
                && Integer.valueOf(row).equals(error.row())
                && column.equals(error.column()) && error.code().equals("INVALID_DATE"));
    }

    private Priority parsePriority(String text) {
        return switch (normalizeHeader(text)) {
            case "low" -> Priority.LOW;
            case "medium", "med" -> Priority.MEDIUM;
            case "high" -> Priority.HIGH;
            case "critical", "urgent" -> Priority.CRITICAL;
            default -> null;
        };
    }

    private TaskStatus parseStatus(String text) {
        return switch (normalizeHeader(text)) {
            case "notstarted", "new", "todo", "open" -> TaskStatus.NOT_STARTED;
            case "inprogress", "inwork", "active" -> TaskStatus.IN_PROGRESS;
            case "blocked", "onhold" -> TaskStatus.BLOCKED;
            case "done", "complete", "completed", "closed" -> TaskStatus.DONE;
            default -> null;
        };
    }

    private IssueType parseIssueType(String text) {
        return switch (normalizeHeader(text)) {
            case "task", "story", "feature", "subtask", "epic" -> IssueType.TASK;
            case "bug", "defect" -> IssueType.DEFECT;
            default -> null;
        };
    }

    private boolean required(String value, String sheet, int row, String column, String code, List<ImportIssue> errors) {
        if (value.isBlank()) {
            errors.add(issue(sheet, row, column, code, column + " is required."));
            return false;
        }
        return true;
    }

    private String value(TabularRow row, Map<Column, Integer> columns, Column column) {
        Integer index = columns.get(column);
        return index == null || index >= row.values().size() ? "" : row.values().get(index).text();
    }

    private CellValue valueCell(TabularRow row, Map<Column, Integer> columns, Column column) {
        Integer index = columns.get(column);
        return index == null || index >= row.values().size() ? CellValue.text("") : row.values().get(index);
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String preserveOptional(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String normalizeHeader(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private String normalizeTaskId(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String projectNameFromFile(String fileName) {
        if (fileName == null) {
            return "";
        }
        String base = java.nio.file.Path.of(fileName).getFileName().toString();
        int extension = base.lastIndexOf('.');
        return (extension > 0 ? base.substring(0, extension) : base).trim();
    }

    private boolean isInstructionSheet(String sheetName) {
        String normalized = normalizeHeader(sheetName);
        return normalized.equals("readme") || normalized.equals("instructions")
                || normalized.equals("instruction") || normalized.equals("howto")
                || normalized.equals("howtouse") || normalized.equals("guide")
                || normalized.equals("about") || normalized.startsWith("readme");
    }

    private void addDuplicateProjectNameErrors(List<ImportProject> projects, List<ImportIssue> errors) {
        Set<String> names = new LinkedHashSet<>();
        for (ImportProject project : projects) {
            if (!names.add(project.name().trim().toLowerCase(Locale.ROOT))) {
                errors.add(issue(project.name(), null, null, "DUPLICATE_PROJECT_NAME",
                        "Project names must be unique within an upload."));
            }
        }
    }

    private ImportIssue issue(String sheet, Integer row, String column, String code, String message) {
        return new ImportIssue(sheet, row, column, code, message);
    }

    private enum Column {
        TASK_ID("Task ID", "taskid", "taskkey"),
        TASK_NAME("Task Name", "taskname", "name"),
        TEAM_MODULE("Team/Module", "teammodule", "team", "module"),
        ASSIGNEE("Assignee", "assignee", "owner"),
        PRIORITY("Priority", "priority"),
        STATUS("Status", "status"),
        START_DATE("Start Date", "startdate"),
        DUE_DATE("Due Date", "duedate", "deadline"),
        BLOCKER_DEPENDENCY("Blocker / Dependency", "blockerdependency", "blocker", "dependency"),
        DESCRIPTION("Description", "description", "details"),
        TYPE("Type", "type", "issuetype", "worktype"),
        MILESTONE("Milestone", "milestone", "release", "fixversion"),
        MILESTONE_DATE("Milestone Date", "milestonedate", "releasedate", "targetdate");

        private final String header;
        private final Set<String> aliases;

        Column(String header, String... aliases) {
            this.header = header;
            this.aliases = Set.of(aliases);
        }

        static Column fromNormalized(String normalized) {
            for (Column column : values()) {
                if (column.aliases.contains(normalized)) {
                    return column;
                }
            }
            return null;
        }
    }

    private record CellValue(String text, LocalDate dateValue) {
        static CellValue text(String text) {
            return new CellValue(text == null ? "" : text, null);
        }
    }

    private record TabularRow(int number, List<CellValue> values) {
    }

    private record TabularSheet(String name, List<String> header, List<TabularRow> rows, int headerRow) {
    }
}