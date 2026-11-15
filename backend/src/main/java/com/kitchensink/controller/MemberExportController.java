package com.kitchensink.controller;



import com.kitchensink.controller.contract.MemberExportControllerInterface;
import com.kitchensink.dto.member.SortSpec;
import com.kitchensink.service.MemberExportService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MemberExportController implements MemberExportControllerInterface {

    private final MemberExportService exportService;

    public MemberExportController(MemberExportService exportService) {
        this.exportService = exportService;
    }

    @Override
    public void export(String q, String sort, HttpServletResponse response) throws IOException {
        SortSpec spec = SortSpec.parse(sort); // validated before any byte is written, so errors stay JSON
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"members.csv\"");
        try (Writer out = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {
            exportService.exportCsv(q, spec, out);
        }
    }
}
