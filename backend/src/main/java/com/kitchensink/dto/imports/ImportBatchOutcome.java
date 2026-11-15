package com.kitchensink.dto.imports;



import java.util.List;

/** Result of writing one batch; row numbers refer to the uploaded sheet. */
public record ImportBatchOutcome(int inserted, List<Integer> duplicateRows, List<Integer> failedRows) {}
