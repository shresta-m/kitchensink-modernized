package com.kitchensink.controller.contract;



import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Members export")
@RequestMapping("/api/v1/members/export")
public interface MemberExportControllerInterface {

    @Operation(summary = "Stream the matching members as CSV (capped at 50,000 rows)")
    @GetMapping
    void export(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "name,asc") String sort,
            HttpServletResponse response) throws IOException;
}
