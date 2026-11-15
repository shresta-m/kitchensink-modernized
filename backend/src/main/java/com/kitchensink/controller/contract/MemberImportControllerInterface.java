package com.kitchensink.controller.contract;



import com.kitchensink.dto.imports.ImportReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Members import")
@RequestMapping("/api/v1/members/import")
public interface MemberImportControllerInterface {

    @Operation(summary = "Bulk import members from an .xlsx file (Name, Email, Phone)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ImportReportResponse upload(@RequestParam("file") MultipartFile file) throws IOException;

    @Operation(summary = "Download the import template")
    @GetMapping("/template")
    ResponseEntity<byte[]> template();
}
