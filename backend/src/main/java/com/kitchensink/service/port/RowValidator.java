package com.kitchensink.service.port;



import com.kitchensink.dto.imports.RawMemberRow;
import com.kitchensink.dto.imports.RowValidation;

/** Port: validates one imported row. */
public interface RowValidator {

    RowValidation validate(RawMemberRow row);
}
