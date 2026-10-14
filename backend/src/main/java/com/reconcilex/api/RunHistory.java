package com.reconcilex.api;
import java.util.List;
public record RunHistory(List<RunResponse> runs,int page,int size,long totalRuns) {}
