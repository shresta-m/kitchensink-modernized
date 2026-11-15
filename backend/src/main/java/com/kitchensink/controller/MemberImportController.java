package com.kitchensink.controller;



import com.kitchensink.controller.contract.MemberImportControllerInterface;
import com.kitchensink.dto.imports.ImportReportResponse;
import com.kitchensink.service.MemberImportService;
import com.kitchensink.validation.XlsxUploadGuard;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MemberImportController implements MemberImportControllerInterface {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final MemberImportService importService;
    private final XlsxUploadGuard uploadGuard;

    public MemberImportController(MemberImportService importService, XlsxUploadGuard uploadGuard) {
        this.importService = importService;
        this.uploadGuard = uploadGuard;
    }

    @Override
    public ImportReportResponse upload(MultipartFile file) throws IOException {
        uploadGuard.require(file);
        try (InputStream in = file.getInputStream()) {
            return ImportReportResponse.from(importService.importMembers(in));
        }
    }

    @Override
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"members-template.xlsx\"")
                .body(importService.template());
    }
}
