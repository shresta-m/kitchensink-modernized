package com.kitchensink.dto.imports;



import java.util.List;

/**
 * Outcome of inserting one batch.
 *
 * @param inserted number of documents written
 * @param duplicateIndexes zero-based positions in the submitted batch rejected by the unique email index
 */
public record BatchInsertResult(int inserted, List<Integer> duplicateIndexes) {}
