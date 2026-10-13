package com.reconcilex.api;
import com.reconcilex.domain.Finding;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public record RunDetails(UUID id, Instant createdAt, String status, RunSummary summary,
                         List<Finding> findings, int page, int size, long totalFindings) {}
