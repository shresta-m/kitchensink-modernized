package com.kitchensink.member.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BulkDeleteResponse {
    private final int deletedCount;
}
