package com.kitchensink.validation;




import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kitchensink.exception.domain.ImportFileException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class XlsxUploadGuardTest {

    private final XlsxUploadGuard guard = new XlsxUploadGuard();

    private static MockMultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "application/octet-stream", bytes);
    }

    @Test
    void acceptsAZipSignatureWithAnXlsxName() {
        assertThatCode(() -> guard.require(file("members.XLSX", new byte[] {'P', 'K', 3, 4}))).doesNotThrowAnyException();
    }

    @Test
    void rejectsWrongExtensionEmptyFilesAndNonZipContent() {
        assertThatThrownBy(() -> guard.require(file("members.csv", new byte[] {'P', 'K'})))
                .isInstanceOf(ImportFileException.class);
        assertThatThrownBy(() -> guard.require(file("members.xlsx", new byte[0])))
                .isInstanceOf(ImportFileException.class);
        assertThatThrownBy(() -> guard.require(file("members.xlsx", "name,email".getBytes())))
                .isInstanceOf(ImportFileException.class);
    }
}
