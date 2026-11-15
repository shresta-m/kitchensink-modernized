package com.kitchensink.service.imports;




import com.kitchensink.dto.imports.RawMemberRow;
import com.kitchensink.exception.ErrorCode;
import com.kitchensink.exception.domain.ImportFileException;
import com.kitchensink.service.port.MemberFileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/** Reads the first sheet of an .xlsx file. Expects headers Name, Email, Phone in row 1 (any column order). */
@Component
public class ExcelMemberReader implements MemberFileReader {

    private final DataFormatter formatter = new DataFormatter();

    @Override
    public List<RawMemberRow> read(InputStream in, int maxRows) {
        try (Workbook workbook = new XSSFWorkbook(in)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw invalid("The workbook has no sheets.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> columns = readHeader(sheet.getRow(0));

            List<RawMemberRow> rows = new ArrayList<>();
            for (Row row : sheet) {
                if (row.getRowNum() == 0) {
                    continue;
                }
                String name = text(row, columns.get("name"));
                String email = text(row, columns.get("email"));
                String phone = text(row, columns.get("phone"));
                if (name.isBlank() && email.isBlank() && phone.isBlank()) {
                    continue; // skip fully blank rows
                }
                if (rows.size() >= maxRows) {
                    throw invalid("The file has more than " + maxRows + " data rows.");
                }
                rows.add(new RawMemberRow(row.getRowNum() + 1, name, email, phone));
            }
            return rows;
        } catch (ImportFileException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw invalid("The file is not a valid .xlsx workbook.");
        }
    }

    private Map<String, Integer> readHeader(Row header) {
        if (header == null) {
            throw invalid("The first row must contain the headers Name, Email and Phone.");
        }
        Map<String, Integer> columns = new HashMap<>();
        for (Cell cell : header) {
            String title = formatter.formatCellValue(cell).trim().toLowerCase(Locale.ROOT);
            columns.putIfAbsent(title, cell.getColumnIndex());
        }
        for (String required : List.of("name", "email", "phone")) {
            if (!columns.containsKey(required)) {
                throw invalid("Missing required column: " + required + ". Use the downloadable template.");
            }
        }
        return columns;
    }

    private String text(Row row, int column) {
        Cell cell = row.getCell(column);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private static ImportFileException invalid(String message) {
        return new ImportFileException(ErrorCode.INVALID_TEMPLATE, message);
    }
}
