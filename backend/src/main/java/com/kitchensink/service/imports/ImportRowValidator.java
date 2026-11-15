package com.kitchensink.service.imports;




import com.kitchensink.dto.imports.ImportIssue;
import com.kitchensink.dto.imports.ImportIssueCode;
import com.kitchensink.dto.imports.RawMemberRow;
import com.kitchensink.dto.imports.RowValidation;
import com.kitchensink.dto.member.MemberRequest;
import com.kitchensink.service.port.RowValidator;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/** Validates a row with the same constraints as the REST API (rules are declared once, on MemberRequest). */
@Component
public class ImportRowValidator implements RowValidator {

    private final Validator validator;

    public ImportRowValidator(Validator validator) {
        this.validator = validator;
    }

    @Override
    public RowValidation validate(RawMemberRow row) {
        MemberRequest details = new MemberRequest(row.name(), row.email(), row.phoneNumber());
        Set<ConstraintViolation<MemberRequest>> violations = validator.validate(details);
        if (violations.isEmpty()) {
            return new RowValidation(details, List.of());
        }
        // One issue per field, messages joined, in stable field order.
        Map<String, List<String>> byField = new TreeMap<>();
        for (ConstraintViolation<MemberRequest> v : violations) {
            byField.computeIfAbsent(v.getPropertyPath().toString(), k -> new ArrayList<>()).add(v.getMessage());
        }
        List<ImportIssue> issues = new ArrayList<>();
        byField.forEach((field, messages) -> {
            messages.sort(String::compareTo);
            issues.add(new ImportIssue(row.rowNumber(), field, ImportIssueCode.VALIDATION_FAILED,
                    String.join("; ", messages)));
        });
        return new RowValidation(details, issues);
    }
}
