package com.kitchensink.service.port;



import com.kitchensink.dto.imports.RawMemberRow;
import java.io.InputStream;
import java.util.List;

/** Port: turns an uploaded file into raw rows. Swap the implementation to support CSV, JSON, etc. */
public interface MemberFileReader {

    List<RawMemberRow> read(InputStream in, int maxRows);
}
