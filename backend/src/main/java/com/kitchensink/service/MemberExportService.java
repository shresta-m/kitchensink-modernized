package com.kitchensink.service;



import com.kitchensink.dto.member.SortSpec;
import java.io.Writer;

public interface MemberExportService {

    /** Hard cap so one request cannot stream the whole database. */
    int MAX_ROWS = 50_000;

    void exportCsv(String text, SortSpec sort, Writer out);
}
