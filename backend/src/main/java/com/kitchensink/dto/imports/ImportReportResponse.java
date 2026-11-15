package com.kitchensink.dto.imports;



import java.util.List;

public record ImportReportResponse(
        int totalRows,
        int inserted,
        int duplicatesSkipped,
        int invalid,
        int failed,
        List<Issue> errors,
        boolean errorsTruncated) {

    public record Issue(int row, String field, String code, String message) {}

    public static ImportReportResponse from(ImportReport r) {
        return new ImportReportResponse(r.totalRows(), r.inserted(), r.duplicatesSkipped(), r.invalid(), r.failed(),
                r.errors().stream().map(i -> new Issue(i.row(), i.field(), i.code().name(), i.message())).toList(),
                r.errorsTruncated());
    }
}
