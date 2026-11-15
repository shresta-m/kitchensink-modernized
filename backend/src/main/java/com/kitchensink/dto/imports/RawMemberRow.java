package com.kitchensink.dto.imports;


/** A spreadsheet row as read, before any validation. */
public record RawMemberRow(int rowNumber, String name, String email, String phoneNumber) {}
