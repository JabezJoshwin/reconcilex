package com.reconcilex.core;

import com.reconcilex.domain.Classification;
import com.reconcilex.domain.Finding;
import com.reconcilex.domain.TransactionRow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ReconciliationEngineTest {
    private final ReconciliationEngine engine = new ReconciliationEngine();

    @Test
    void partitionsDistinctIdsIntoAllNineClassifications() {
        List<TransactionRow> internal = List.of(row("match", 1, "INR"), row("amount", 1, "INR"),
                row("currency", 1, "INR"), row("both", 1, "INR"), row("missing", 1, "INR"),
                row("dupInternal", 1, "INR"), row("dupInternal", 2, "USD"),
                row("dupProcessor", 1, "INR"), row("dupBoth", 1, "INR"), row("dupBoth", 1, "INR"));
        List<TransactionRow> processor = List.of(row("match", 1, "INR"), row("amount", 2, "INR"),
                row("currency", 1, "USD"), row("both", 2, "USD"), row("unexpected", 1, "INR"),
                row("dupInternal", 1, "INR"), row("dupProcessor", 1, "INR"), row("dupProcessor", 1, "INR"),
                row("dupBoth", 1, "INR"), row("dupBoth", 2, "INR"));
        Map<String, Finding> results = engine.reconcile(internal, processor).stream()
                .collect(Collectors.toMap(Finding::transactionId, Function.identity()));
        assertEquals(9, results.size());
        assertEquals(Classification.MATCHED, results.get("match").classification());
        assertEquals(Classification.AMOUNT_MISMATCH, results.get("amount").classification());
        assertEquals(Classification.CURRENCY_MISMATCH, results.get("currency").classification());
        assertEquals(Classification.AMOUNT_AND_CURRENCY_MISMATCH, results.get("both").classification());
        assertEquals(Classification.MISSING_IN_PROCESSOR, results.get("missing").classification());
        assertEquals(Classification.UNEXPECTED_IN_PROCESSOR, results.get("unexpected").classification());
        assertEquals(Classification.DUPLICATE_INTERNAL, results.get("dupInternal").classification());
        assertEquals(Classification.DUPLICATE_PROCESSOR, results.get("dupProcessor").classification());
        assertEquals(Classification.DUPLICATE_BOTH, results.get("dupBoth").classification());
        assertNull(results.get("dupInternal").internalAmountMinor());
        assertNull(results.get("dupInternal").internalCurrency());
        assertEquals(1L, results.get("dupInternal").processorAmountMinor());
        assertEquals(2, results.get("dupInternal").internalOccurrences());
        assertNull(results.get("dupProcessor").processorAmountMinor());
        assertNull(results.get("dupBoth").internalAmountMinor());
        assertNull(results.get("dupBoth").processorAmountMinor());
        assertEquals(0, results.get("missing").processorOccurrences());
        assertEquals(0, results.get("unexpected").internalOccurrences());
    }

    @Test
    void duplicatesTakePrecedenceEvenWhenOtherSideIsMissing() {
        TransactionRow duplicate = row("a", 1, "INR");
        assertEquals(Classification.DUPLICATE_INTERNAL,
                engine.reconcile(List.of(duplicate, duplicate), List.of()).getFirst().classification());
        assertEquals(Classification.DUPLICATE_PROCESSOR,
                engine.reconcile(List.of(), List.of(duplicate, duplicate)).getFirst().classification());
    }

    @Test
    void emptyInputsProduceEmptyResults() {
        assertTrue(engine.reconcile(List.of(), List.of()).isEmpty());
    }

    @Test
    void outputIsSortedAndIndependentOfInputOrderAndRepeatedRuns() {
        List<TransactionRow> internal = new ArrayList<>(List.of(row("z", 1, "INR"), row("a", 2, "USD"),
                row("m", 1, "INR"), row("m", 9, "USD")));
        List<TransactionRow> processor = new ArrayList<>(List.of(row("z", 2, "USD"), row("a", 2, "USD")));
        List<Finding> expected = engine.reconcile(internal, processor);
        assertEquals(List.of("a", "m", "z"), expected.stream().map(Finding::transactionId).toList());
        assertEquals(expected, engine.reconcile(internal, processor));
        Collections.reverse(internal);
        Collections.reverse(processor);
        assertEquals(expected, engine.reconcile(internal, processor));
    }

    @Test
    void idsAreExactAndLongAmountsDoNotLosePrecision() {
        List<Finding> findings = engine.reconcile(List.of(row("a", Long.MAX_VALUE, "INR"), row(" a", 1, "INR")),
                List.of(row("a", Long.MAX_VALUE - 1, "INR")));
        assertEquals(2, findings.size());
        assertEquals(Classification.MISSING_IN_PROCESSOR, findings.get(0).classification());
        assertEquals(Classification.AMOUNT_MISMATCH, findings.get(1).classification());
        assertEquals(Long.MAX_VALUE, findings.get(1).internalAmountMinor());
    }

    private static TransactionRow row(String id, long amount, String currency) {
        return new TransactionRow(id, amount, currency);
    }
}
