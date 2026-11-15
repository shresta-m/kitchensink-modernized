package com.kitchensink.dto.imports;


/** One problem found in one spreadsheet row. {@code row} is the Excel row number (header = 1). */
public record ImportIssue(int row, String field, ImportIssueCode code, String message) {}
