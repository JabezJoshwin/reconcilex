package com.reconcilex.core;

import com.reconcilex.domain.Classification;
import com.reconcilex.domain.Finding;
import com.reconcilex.domain.TransactionRow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Expected linear grouping and matching; sorting provides a stable report order. */
public final class ReconciliationEngine {
    public List<Finding> reconcile(List<TransactionRow> internal, List<TransactionRow> processor) {
        Map<String, Occurrences> internalById = group(internal);
        Map<String, Occurrences> processorById = group(processor);
        Set<String> ids = new HashSet<>(internalById.keySet());
        ids.addAll(processorById.keySet());
        List<Finding> findings = new ArrayList<>(ids.size());
        for (String id : ids.stream().sorted().toList()) {
            Occurrences left = internalById.get(id);
            Occurrences right = processorById.get(id);
            TransactionRow leftValue = unique(left);
            TransactionRow rightValue = unique(right);
            findings.add(new Finding(id, classify(left, right),
                    leftValue == null ? null : leftValue.amountMinor(),
                    leftValue == null ? null : leftValue.currency(),
                    rightValue == null ? null : rightValue.amountMinor(),
                    rightValue == null ? null : rightValue.currency(),
                    left == null ? 0 : left.count, right == null ? 0 : right.count));
        }
        return List.copyOf(findings);
    }

    private static Map<String, Occurrences> group(List<TransactionRow> rows) {
        Map<String, Occurrences> grouped = new HashMap<>();
        for (TransactionRow row : rows) {
            Occurrences occurrences = grouped.computeIfAbsent(row.transactionId(), ignored -> new Occurrences(row));
            occurrences.count++;
        }
        return grouped;
    }

    private static TransactionRow unique(Occurrences occurrences) {
        return occurrences != null && occurrences.count == 1 ? occurrences.first : null;
    }

    private static Classification classify(Occurrences left, Occurrences right) {
        boolean duplicateLeft = left != null && left.count > 1;
        boolean duplicateRight = right != null && right.count > 1;
        if (duplicateLeft && duplicateRight) return Classification.DUPLICATE_BOTH;
        if (duplicateLeft) return Classification.DUPLICATE_INTERNAL;
        if (duplicateRight) return Classification.DUPLICATE_PROCESSOR;
        if (left == null) return Classification.UNEXPECTED_IN_PROCESSOR;
        if (right == null) return Classification.MISSING_IN_PROCESSOR;
        boolean amountDiffers = left.first.amountMinor() != right.first.amountMinor();
        boolean currencyDiffers = !left.first.currency().equals(right.first.currency());
        if (amountDiffers && currencyDiffers) return Classification.AMOUNT_AND_CURRENCY_MISMATCH;
        if (amountDiffers) return Classification.AMOUNT_MISMATCH;
        if (currencyDiffers) return Classification.CURRENCY_MISMATCH;
        return Classification.MATCHED;
    }

    private static final class Occurrences {
        private final TransactionRow first;
        private int count;
        private Occurrences(TransactionRow first) { this.first = first; }
    }
}
