package com.kitchensink.service.imports;



import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

/** Remembers the first row that used each key. One instance per import; not thread-safe by design. */
public final class InFileDuplicateTracker {

    private final Map<String, Integer> firstRowByKey = new HashMap<>();

    /** Records the key and returns empty, or returns the row number of its first occurrence. */
    public OptionalInt firstSeenAt(String key, int rowNumber) {
        Integer first = firstRowByKey.putIfAbsent(key, rowNumber);
        return first == null ? OptionalInt.empty() : OptionalInt.of(first);
    }
}
