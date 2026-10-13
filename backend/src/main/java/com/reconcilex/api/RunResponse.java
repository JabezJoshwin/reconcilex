package com.reconcilex.api;
import java.time.Instant;
import java.util.UUID;
public record RunResponse(UUID id, Instant createdAt, String status, RunSummary summary) {}
