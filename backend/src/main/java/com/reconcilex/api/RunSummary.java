package com.reconcilex.api;
import com.reconcilex.domain.Classification;
import java.util.Map;
public record RunSummary(int internalRows, int processorRows, int distinctIds, Map<Classification, Long> counts) {}
