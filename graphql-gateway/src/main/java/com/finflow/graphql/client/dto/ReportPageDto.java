package com.finflow.graphql.client.dto;

import java.util.List;

public record ReportPageDto(
        List<ReportDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {}
