package com.reconcilex.core;

import com.reconcilex.domain.InputValidationException;
import com.reconcilex.domain.TransactionRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvTransactionParserTest {
    private static final String HEADER = "transaction_id,amount_minor,currency\n";
    private final CsvTransactionParser parser = new CsvTransactionParser();

    @Test
    void parsesPositiveLongMaximumAndQuotedIdsWithoutNormalizing() {
        assertEquals(List.of(new TransactionRow(" pay,001 ", Long.MAX_VALUE, "INR")),
                parse(HEADER + "\" pay,001 \",9223372036854775807,INR\n"));
    }

    @Test
    void headerOnlyInputIsValidAndBomIsAccepted() {
        assertEquals(List.of(), parse(HEADER));
        assertEquals(List.of(), parse("\uFEFF" + HEADER));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "amount_minor,transaction_id,currency\n", "transaction_id,amount_minor,currency,extra\n",
            "transaction_id,amount_minor,currency\na,1,INR,extra\n", "transaction_id,amount_minor,currency\na,1\n",
            "transaction_id,amount_minor,currency\n\n", "transaction_id,amount_minor,currency\n\"unclosed,1,INR"})
    void rejectsMalformedStructure(String csv) {
        assertThrows(InputValidationException.class, () -> parse(csv));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "+1", "1.0", "1e3", "", " 1", "1 ", "9223372036854775808", "NaN"})
    void rejectsInvalidAmounts(String amount) {
        assertThrows(InputValidationException.class, () -> parse(HEADER + "a," + amount + ",INR\n"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"inr", "ZZZ", "", "US", " USD", "USD "})
    void rejectsInvalidCurrencies(String currency) {
        assertThrows(InputValidationException.class, () -> parse(HEADER + "a,1," + currency + "\n"));
    }

    @Test
    void rejectsBlankLongAndControlCharacterIds() {
        for (String id : List.of("", "   ", "a".repeat(129), "a\tb", "a\u0000b")) {
            assertThrows(InputValidationException.class, () -> parse(HEADER + id + ",1,INR\n"));
        }
        assertThrows(InputValidationException.class, () -> parse(HEADER + "\"a\nb\",1,INR\n"));
        assertEquals(128, parse(HEADER + "a".repeat(128) + ",1,INR\n").getFirst().transactionId().length());
    }

    @Test
    void rejectsMalformedUtf8WithoutLeakingData() {
        byte[] header = HEADER.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = java.util.Arrays.copyOf(header, header.length + 2);
        bytes[header.length] = (byte) 0xC3;
        bytes[header.length + 1] = (byte) 0x28;
        InputValidationException error = assertThrows(InputValidationException.class,
                () -> parser.parse(new ByteArrayInputStream(bytes), "internalFile"));
        assertTrue(error.getMessage().contains("UTF-8"));
    }

    @Test
    void rejectsOversizedFiles() {
        byte[] bytes = new byte[CsvTransactionParser.MAX_BYTES + 1];
        assertThrows(InputValidationException.class,
                () -> parser.parse(new ByteArrayInputStream(bytes), "internalFile"));
    }

    @Test
    void acceptsRowLimitAndRejectsNextRowEvenIfIdIsDuplicate() {
        String max = HEADER + "a,1,INR\n".repeat(CsvTransactionParser.MAX_ROWS);
        assertEquals(CsvTransactionParser.MAX_ROWS, parse(max).size());
        assertThrows(InputValidationException.class, () -> parse(max + "a,1,INR\n"));
    }

    @Test
    void preservesDuplicatesForMatchingAndNeverIncludesUploadedContentsInErrors() {
        assertEquals(2, parse(HEADER + "a,1,INR\na,2,INR\n").size());
        InputValidationException error = assertThrows(InputValidationException.class,
                () -> parse(HEADER + "PRIVATE_PAYMENT,SECRET_AMOUNT,INR\n"));
        assertFalse(error.getMessage().contains("PRIVATE_PAYMENT"));
        assertFalse(error.getMessage().contains("SECRET_AMOUNT"));
    }

    private List<TransactionRow> parse(String csv) {
        return parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), "internalFile");
    }
}
