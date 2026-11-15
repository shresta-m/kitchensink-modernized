package com.kitchensink.service.imports;




import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kitchensink.dto.imports.RawMemberRow;
import com.kitchensink.exception.ErrorCode;
import com.kitchensink.exception.domain.ImportFileException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExcelTest {

    private final ExcelMemberReader reader = new ExcelMemberReader();

    private static byte[] workbook(Consumer<XSSFWorkbook> fill) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            fill.accept(wb);
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static void row(Sheet sheet, int index, String... values) {
        Row r = sheet.createRow(index);
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                r.createCell(i).setCellValue(values[i]);
            }
        }
    }

    private List<RawMemberRow> read(byte[] bytes, int max) {
        return reader.read(new ByteArrayInputStream(bytes), max);
    }

    @Test
    void readsRowsInAnyColumnOrderIgnoringHeaderCaseAndBlankRows() throws Exception {
        byte[] file = workbook(wb -> {
            Sheet s = wb.createSheet();
            row(s, 0, " PHONE ", "name", "Email");
            row(s, 1, "2125551212", "Ann", "ann@example.com");
            row(s, 2); // fully blank row
            row(s, 3, "2125559999", "Bob", null); // missing email cell
        });

        List<RawMemberRow> rows = read(file, 10);

        assertThat(rows).containsExactly(
                new RawMemberRow(2, "Ann", "ann@example.com", "2125551212"),
                new RawMemberRow(4, "Bob", "", "2125559999"));
    }

    @Test
    void numericCellsAreReadAsDisplayedText() throws Exception {
        byte[] file = workbook(wb -> {
            Sheet s = wb.createSheet();
            row(s, 0, "Name", "Email", "Phone");
            Row r = s.createRow(1);
            r.createCell(0).setCellValue("Ann");
            r.createCell(1).setCellValue("ann@example.com");
            r.createCell(2).setCellValue(2125551212d);
        });

        assertThat(read(file, 10).get(0).phoneNumber()).isEqualTo("2125551212");
    }

    @Test
    void rejectsMoreRowsThanTheLimit() throws Exception {
        byte[] file = workbook(wb -> {
            Sheet s = wb.createSheet();
            row(s, 0, "Name", "Email", "Phone");
            row(s, 1, "A", "a@b.co", "2125551212");
            row(s, 2, "B", "b@b.co", "2125551213");
        });

        assertThatThrownBy(() -> read(file, 1))
                .isInstanceOfSatisfying(ImportFileException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.INVALID_TEMPLATE);
                    assertThat(e.getMessage()).contains("more than 1");
                });
        assertThat(read(file, 2)).hasSize(2);
    }

    @Test
    void rejectsMissingHeaderRowMissingColumnsAndEmptyWorkbooks() throws Exception {
        byte[] noHeader = workbook(wb -> row(wb.createSheet(), 3, "x"));
        byte[] missingColumn = workbook(wb -> row(wb.createSheet(), 0, "Name", "Email"));
        byte[] noSheets = workbook(wb -> { });

        assertThatThrownBy(() -> read(noHeader, 10)).isInstanceOf(ImportFileException.class).hasMessageContaining("headers");
        assertThatThrownBy(() -> read(missingColumn, 10)).isInstanceOf(ImportFileException.class)
                .hasMessageContaining("phone");
        assertThatThrownBy(() -> read(noSheets, 10)).isInstanceOf(ImportFileException.class);
    }

    @Test
    void rejectsFilesThatAreNotWorkbooks() {
        assertThatThrownBy(() -> read("definitely not a zip".getBytes(), 10))
                .isInstanceOf(ImportFileException.class)
                .hasMessageContaining("not a valid .xlsx");
    }

    @Test
    void templateRoundTripsThroughTheReader() {
        byte[] template = new ExcelTemplateWriter().template();

        List<RawMemberRow> rows = read(template, 10);

        assertThat(rows).containsExactly(new RawMemberRow(2, "Jane Doe", "jane.doe@example.com", "2125551234"));
    }
}
