package com.kitchensink.service.impl;



import com.kitchensink.dto.member.MemberSearchCriteria;
import com.kitchensink.dto.member.SortSpec;
import com.kitchensink.service.MemberExportService;
import com.kitchensink.service.port.MemberRowFormatter;
import com.kitchensink.service.port.MemberStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import org.springframework.stereotype.Service;

@Service
public class MemberExportServiceImpl implements MemberExportService {

    private final MemberStore members;
    private final MemberRowFormatter csv;

    public MemberExportServiceImpl(MemberStore members, MemberRowFormatter csv) {
        this.members = members;
        this.csv = csv;
    }

    @Override
    public void exportCsv(String text, SortSpec sort, Writer out) {
        MemberSearchCriteria criteria = MemberSearchCriteria.of(text);
        csv.writeHeader(out);
        members.forEachMatching(criteria, sort, MAX_ROWS, member -> csv.writeRow(out, member));
        try {
            out.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
