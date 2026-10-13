package com.reconcilex.core;

import com.reconcilex.domain.InputValidationException;
import com.reconcilex.domain.TransactionRow;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Validates each source before any reconciliation or persistence takes place. */
public final class CsvTransactionParser {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public static final int MAX_ROWS = 20_000;
    private static final List<String> HEADER = List.of("transaction_id", "amount_minor", "currency");
    private static final Set<String> CURRENCIES = Currency.getAvailableCurrencies().stream()
            .map(Currency::getCurrencyCode).collect(Collectors.toUnmodifiableSet());
    private static final CSVFormat FORMAT = CSVFormat.RFC4180.builder()
            .setIgnoreEmptyLines(false).get();

    public List<TransactionRow> parse(InputStream input, String source) {
        final String contents;
        try {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) {
                throw invalid(source, "file exceeds the 5 MiB limit");
            }
            contents = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw invalid(source, "file must contain valid UTF-8");
        } catch (IOException exception) {
            throw invalid(source, "file could not be read");
        }

        // A UTF-8 BOM is an encoding marker, not part of the first header name.
        String csv = contents.startsWith("\uFEFF") ? contents.substring(1) : contents;
        validateQuoteStructure(csv, source);
        List<TransactionRow> rows = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(csv, FORMAT)) {
            boolean headerRead = false;
            for (CSVRecord record : parser) {
                if (!headerRead) {
                    if (!record.toList().equals(HEADER)) {
                        throw invalid(source, "expected header transaction_id,amount_minor,currency");
                    }
                    headerRead = true;
                    continue;
                }
                long rowNumber = record.getRecordNumber();
                if (rows.size() == MAX_ROWS) {
                    throw invalid(source, "file exceeds the 20,000 data row limit");
                }
                if (record.size() != 3) {
                    throw invalid(source, "row " + rowNumber + " must contain exactly three columns");
                }
                String id = record.get(0);
                if (id.isBlank() || id.length() > 128 || id.codePoints().anyMatch(Character::isISOControl)) {
                    throw invalid(source, "row " + rowNumber + " transaction_id must be nonblank, at most 128 characters, and contain no control characters");
                }
                String amountText = record.get(1);
                if (!amountText.matches("[0-9]+")) {
                    throw invalid(source, "row " + rowNumber + " amount_minor must be a positive 64-bit integer written as decimal digits");
                }
                long amount;
                try {
                    amount = Long.parseLong(amountText);
                } catch (NumberFormatException exception) {
                    throw invalid(source, "row " + rowNumber + " amount_minor exceeds the signed 64-bit integer range");
                }
                if (amount <= 0) {
                    throw invalid(source, "row " + rowNumber + " amount_minor must be strictly positive");
                }
                String currency = record.get(2);
                if (!CURRENCIES.contains(currency)) {
                    throw invalid(source, "row " + rowNumber + " currency must be a valid uppercase ISO 4217 code");
                }
                rows.add(new TransactionRow(id, amount, currency));
            }
            if (!headerRead) {
                throw invalid(source, "expected header transaction_id,amount_minor,currency");
            }
        } catch (IOException | UncheckedIOException | IllegalArgumentException exception) {
            // Library errors can contain raw uploaded values, so do not expose them.
            throw invalid(source, "malformed CSV structure");
        }
        return List.copyOf(rows);
    }

    private static InputValidationException invalid(String source, String message) {
        return new InputValidationException(source + ": " + message);
    }

    // Commons CSV accepts quotes inside bare fields and whitespace after closing quotes.
    // Enforce our strict structure before the library decodes field values.
    private static void validateQuoteStructure(String csv, String source) {
        int state = 0; // 0 field start; 1 unquoted; 2 quoted; 3 closed quote
        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (state == 2) {
                if (c == '"') {
                    if (i + 1 < csv.length() && csv.charAt(i + 1) == '"') i++;
                    else state = 3;
                }
            } else if (c == ',' || c == '\n' || c == '\r') {
                state = 0;
            } else if (c == '"') {
                if (state != 0) throw invalid(source, "malformed CSV quoting");
                state = 2;
            } else {
                if (state == 3) throw invalid(source, "malformed CSV quoting");
                state = 1;
            }
        }
        if (state == 2) throw invalid(source, "malformed CSV quoting");
    }
}
