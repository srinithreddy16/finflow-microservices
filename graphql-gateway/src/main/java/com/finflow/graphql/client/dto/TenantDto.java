package com.finflow.graphql.client.dto;

import java.time.Instant;

public record TenantDto(
        String id, String name, String slug, String ownerEmail, String status, Instant createdAt) {}
