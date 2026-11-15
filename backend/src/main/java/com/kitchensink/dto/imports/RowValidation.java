package com.kitchensink.dto.imports;



import com.kitchensink.dto.member.MemberRequest;
import java.util.List;

public record RowValidation(MemberRequest details, List<ImportIssue> issues) {

    public boolean isValid() {
        return issues.isEmpty();
    }
}
