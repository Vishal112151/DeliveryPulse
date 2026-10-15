package com.deliverypulse.upload;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UploadService {

    private static final Logger logger = LoggerFactory.getLogger(UploadService.class);
    private static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024;

    private final ProjectImportParser parser;
    private final ImportPersistenceService persistenceService;

    public UploadService(ProjectImportParser parser, ImportPersistenceService persistenceService) {
        this.parser = parser;
        this.persistenceService = persistenceService;
    }

    public ImportSummary importExcel(MultipartFile file) {
        String fileName = validateUpload(file);
        String extension = extension(fileName);
        if (!extension.equals("xlsx") && !extension.equals("xls")) {
            throw new UploadRejectedException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Only .xlsx and .xls Excel workbooks are supported by this endpoint.");
        }
        try (InputStream input = file.getInputStream()) {
            ImportDocument document = parser.parseExcel(input, fileName);
            return persistAndLog(fileName, extension, document);
        } catch (IOException exception) {
            throw new UploadRejectedException("The Excel upload could not be read.");
        }
    }

    public ImportSummary importCsv(MultipartFile file) {
        String fileName = validateUpload(file);
        if (!extension(fileName).equals("csv")) {
            throw new UploadRejectedException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Only .csv files are supported by this endpoint.");
        }
        try (InputStream input = file.getInputStream()) {
            ImportDocument document = parser.parseCsv(input, fileName);
            return persistAndLog(fileName, "csv", document);
        } catch (IOException exception) {
            throw new UploadRejectedException("The CSV upload could not be read.");
        }
    }

    private String validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new UploadRejectedException("Upload must include a non-empty file.");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new UploadRejectedException(HttpStatus.PAYLOAD_TOO_LARGE, "Upload exceeds the 10 MB limit.");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new UploadRejectedException("The uploaded file must have a filename.");
        }
        String fileName = originalName.replace('\\', '/');
        fileName = fileName.substring(fileName.lastIndexOf('/') + 1).trim();
        if (fileName.isBlank() || fileName.length() > 255) {
            throw new UploadRejectedException("Filename must be between 1 and 255 characters.");
        }
        return fileName;
    }

    private ImportSummary persistAndLog(String fileName, String fileType, ImportDocument document) {
        ImportSummary summary = persistenceService.persist(fileName, fileType, document);
        if (summary.hasErrors()) {
            logger.warn("Project data import rejected: type={}, errors={}, warnings={}",
                    fileType, summary.errors().size(), summary.warnings().size());
        } else {
            logger.info("Project data import completed: type={}, projects={}, tasks={}, warnings={}",
                    fileType, summary.projectCount(), summary.importedTaskCount(), summary.warnings().size());
        }
        return summary;
    }

    private String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}