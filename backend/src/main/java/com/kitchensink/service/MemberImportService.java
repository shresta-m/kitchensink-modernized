package com.kitchensink.service;



import com.kitchensink.dto.imports.ImportReport;
import java.io.InputStream;

public interface MemberImportService {

    ImportReport importMembers(InputStream file);

    /** The downloadable .xlsx template. */
    byte[] template();
}
