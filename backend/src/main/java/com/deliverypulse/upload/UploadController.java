package com.deliverypulse.upload;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/uploads")
@Tag(name = "Uploads", description = "Import project task data from Excel workbooks and CSV files.")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping(value = "/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import projects from an Excel workbook",
            description = "Reads each relevant worksheet as a project. Instruction sheets are skipped. The upload is atomic: validation errors import no data.")
    public ResponseEntity<ImportSummary> uploadExcel(
            @Parameter(description = "An .xlsx or .xls workbook, up to 10 MB")
            @RequestPart("file") MultipartFile file) {
        return response(uploadService.importExcel(file));
    }

    @PostMapping(value = "/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import one project from a CSV file",
            description = "Uses the CSV filename (without extension) as the project name. The upload is atomic: validation errors import no data.")
    public ResponseEntity<ImportSummary> uploadCsv(
            @Parameter(description = "A UTF-8 .csv file, up to 10 MB")
            @RequestPart("file") MultipartFile file) {
        return response(uploadService.importCsv(file));
    }

    private ResponseEntity<ImportSummary> response(ImportSummary summary) {
        if (summary.hasErrors()) {
            boolean duplicate = summary.errors().stream().anyMatch(issue -> issue.code().equals("PROJECT_ALREADY_EXISTS"));
            return ResponseEntity.status(duplicate ? HttpStatus.CONFLICT : HttpStatus.UNPROCESSABLE_ENTITY).body(summary);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(summary);
    }
}