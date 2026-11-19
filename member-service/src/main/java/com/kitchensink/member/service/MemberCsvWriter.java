package com.kitchensink.member.service;

import com.kitchensink.member.model.Member;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Formats members as CSV. Only this class knows about CSV rules:
 * <ul>
 *   <li>UTF-8 with a BOM, so Excel shows non-ASCII names correctly;</li>
 *   <li>RFC 4180 quoting for commas, quotes and line breaks;</li>
 *   <li>free-text cells starting with = + - @ are prefixed with ' so spreadsheets never run them as formulas.</li>
 * </ul>
 * Password hashes and internal fields are never written.
 */
@Component
public class MemberCsvWriter {

    private static final String BOM = "﻿";
    private static final String NEWLINE = "\r\n";
    private static final String FORMULA_TRIGGERS = "=+-@\t\r";
    private static final List<String> HEADER = List.of("Name", "Email", "Country code", "Phone", "Role", "Status", "Added");

    public byte[] write(List<Member> members) {
        StringBuilder csv = new StringBuilder(BOM);
        csv.append(String.join(",", HEADER)).append(NEWLINE);
        for (Member m : members) {
            csv.append(freeText(m.getName())).append(',')
               .append(freeText(m.getEmail())).append(',')
               .append(plain(m.getCountryCode())).append(',')     // validated formats: no formula risk
               .append(plain(m.getPhoneNumber())).append(',')
               .append(plain(m.getRole())).append(',')
               .append(plain(m.getStatus())).append(',')
               .append(plain(m.getCreatedAt())).append(NEWLINE);
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** User-entered text: neutralize spreadsheet formulas, then quote if needed. */
    private static String freeText(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String safe = FORMULA_TRIGGERS.indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        return quoteIfNeeded(safe);
    }

    private static String plain(Object value) {
        return value == null ? "" : quoteIfNeeded(value.toString());
    }

    private static String quoteIfNeeded(String value) {
        boolean needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        return needsQuotes ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }
}
