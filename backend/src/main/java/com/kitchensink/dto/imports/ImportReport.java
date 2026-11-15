package com.kitchensink.dto.imports;



import java.util.List;

/**
 * @param invalid rows that failed validation
 * @param duplicatesSkipped rows skipped because the email repeats in the file or already exists
 * @param failed rows that were valid but could not be saved (infrastructure error in their batch)
 */
public record ImportReport(
        int totalRows,
        int inserted,
        int duplicatesSkipped,
        int invalid,
        int failed,
        List<ImportIssue> errors,
        boolean errorsTruncated) {}
