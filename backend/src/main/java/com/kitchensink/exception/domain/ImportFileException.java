package com.kitchensink.exception.domain;


import com.kitchensink.exception.ErrorCode;

/** A file-level import problem (wrong type, too big, bad headers). Row-level problems go in the report. */
public class ImportFileException extends DomainException {
    public ImportFileException(ErrorCode code, String message) {
        super(code, message);
    }
}
