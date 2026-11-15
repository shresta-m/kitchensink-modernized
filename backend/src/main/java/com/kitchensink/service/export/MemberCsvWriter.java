package com.kitchensink.service.export;




import com.kitchensink.model.Member;
import com.kitchensink.service.port.MemberRowFormatter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import org.springframework.stereotype.Component;

/** RFC 4180 CSV with every cell quoted and spreadsheet formula injection neutralized. */
@Component
public class MemberCsvWriter implements MemberRowFormatter {

    @Override
    public void writeHeader(Writer out) {
        write(out, "name,email,phoneNumber,createdAt\r\n");
    }

    @Override
    public void writeRow(Writer out, Member m) {
        write(out, cell(m.getName()) + "," + cell(m.getEmail()) + "," + cell(m.getPhoneNumber()) + ","
                + cell(String.valueOf(m.getCreatedAt())) + "\r\n");
    }

    /** Package-visible for tests. */
    static String cell(String value) {
        String v = value == null ? "" : value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    private static void write(Writer out, String s) {
        try {
            out.write(s);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
