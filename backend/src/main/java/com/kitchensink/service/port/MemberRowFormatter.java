package com.kitchensink.service.port;



import com.kitchensink.model.Member;
import java.io.Writer;

/** Port: writes members to an output format (CSV today). */
public interface MemberRowFormatter {

    void writeHeader(Writer out);

    void writeRow(Writer out, Member member);
}
