package com.kitchensink.exception.domain;


import com.kitchensink.exception.ErrorCode;

public class MemberNotFoundException extends NotFoundException {
    public MemberNotFoundException(String id) {
        super(ErrorCode.MEMBER_NOT_FOUND, "No member exists with the given id.");
    }
}
