package com.kitchensink.validation;



import com.kitchensink.exception.ErrorCode;
import com.kitchensink.exception.domain.ImportFileException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Rejects uploads that are not .xlsx: checks the extension and the ZIP magic bytes ("PK"), not the content type. */
@Component
public class XlsxUploadGuard {

    public void require(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (file.isEmpty() || !name.endsWith(".xlsx")) {
            throw new ImportFileException(ErrorCode.UNSUPPORTED_FILE_TYPE, "Only .xlsx files are accepted.");
        }
        try (InputStream in = file.getInputStream()) {
            byte[] magic = in.readNBytes(2);
            if (magic.length < 2 || magic[0] != 'P' || magic[1] != 'K') {
                throw new ImportFileException(ErrorCode.UNSUPPORTED_FILE_TYPE, "The file is not an .xlsx workbook.");
            }
        }
    }
}
