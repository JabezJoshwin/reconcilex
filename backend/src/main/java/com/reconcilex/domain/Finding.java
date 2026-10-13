package com.reconcilex.domain;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
public record Finding(String transactionId, Classification classification,
                      @JsonSerialize(using=ToStringSerializer.class) Long internalAmountMinor, String internalCurrency,
                      @JsonSerialize(using=ToStringSerializer.class) Long processorAmountMinor, String processorCurrency,
                      int internalOccurrences, int processorOccurrences) {}
