package com.kitchensink.service.imports;




import com.kitchensink.service.port.ImportTemplateProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class ExcelTemplateWriter implements ImportTemplateProvider {

    @Override
    public byte[] template() {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Members");

            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle header = wb.createCellStyle();
            header.setFont(bold);
            // Phone column as text so leading zeros survive.
            CellStyle text = wb.createCellStyle();
            text.setDataFormat(wb.createDataFormat().getFormat("@"));
            sheet.setDefaultColumnStyle(2, text);

            Row head = sheet.createRow(0);
            String[] titles = {"Name", "Email", "Phone"};
            for (int i = 0; i < titles.length; i++) {
                head.createCell(i).setCellValue(titles[i]);
                head.getCell(i).setCellStyle(header);
            }
            Row example = sheet.createRow(1);
            example.createCell(0).setCellValue("Jane Doe");
            example.createCell(1).setCellValue("jane.doe@example.com");
            example.createCell(2).setCellValue("2125551234");
            for (int i = 0; i < titles.length; i++) {
                sheet.setColumnWidth(i, 28 * 256);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
