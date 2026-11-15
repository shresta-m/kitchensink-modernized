package com.kitchensink.dto.member;



import java.util.List;

public record BulkDeleteResponse(int requested, int deleted, List<String> notFound) {}
