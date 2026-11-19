package com.kitchensink.member.service;

import java.util.Arrays;
import java.util.Objects;

/** A generated CSV file: the suggested download name and its UTF-8 bytes. */
public record CsvExport(String filename, byte[] content) {
    @Override
    public boolean equals(Object o) {
        return this == o
                || (o instanceof CsvExport(String otherName, byte[] otherContent)
                && Objects.equals(filename, otherName)
                && Arrays.equals(content, otherContent));
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hashCode(filename) + Arrays.hashCode(content);
    }

    @Override
    public String toString() {
        return "CsvExport[filename=" + filename + ", bytes=" + content.length + "]";
    }
}
