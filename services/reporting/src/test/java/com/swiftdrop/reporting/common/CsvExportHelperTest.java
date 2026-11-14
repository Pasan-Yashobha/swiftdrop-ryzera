package com.swiftdrop.reporting.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvExportHelperTest {

    @Test
    @DisplayName("T8: CSV formula escaping prefixes dangerous initial characters (=, +, -, @) with apostrophe")
    void testCsvFormulaEscaping() {
        assertThat(CsvExportHelper.escapeCell("=SUM(A1:B2)")).isEqualTo("'=SUM(A1:B2)");
        assertThat(CsvExportHelper.escapeCell("+12345")).isEqualTo("'+12345");
        assertThat(CsvExportHelper.escapeCell("-calc")).isEqualTo("'-calc");
        assertThat(CsvExportHelper.escapeCell("@cmd")).isEqualTo("'@cmd");
        assertThat(CsvExportHelper.escapeCell("SafeValue")).isEqualTo("SafeValue");
    }

    @Test
    @DisplayName("CSV generation properly quotes commas and double quotes")
    void testCsvQuotesAndCommas() {
        List<String> headers = List.of("Name", "Note");
        List<List<Object>> rows = List.of(
                List.of("Alice, Bob", "Said \"Hello\""),
                List.of("=1+1", "Normal")
        );

        String csv = CsvExportHelper.toCsv(headers, rows);
        assertThat(csv).contains("\"Alice, Bob\"");
        assertThat(csv).contains("\"Said \"\"Hello\"\"\"");
        assertThat(csv).contains("'=1+1");
    }
}
