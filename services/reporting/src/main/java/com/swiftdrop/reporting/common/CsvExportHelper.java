package com.swiftdrop.reporting.common;

import java.util.List;

/**
 * Utility for producing CSV output safely.
 *
 * <p>Brief §5: "Escape any cell starting with '=', '+', '-' or '@' by prefixing an apostrophe."
 * Prevents CSV formula injection / spreadsheet exploitation.
 */
public final class CsvExportHelper {

    private CsvExportHelper() {}

    /**
     * Escapes a single cell value for CSV formatting and spreadsheet formula safety.
     */
    public static String escapeCell(Object value) {
        if (value == null) {
            return "";
        }
        String str = value.toString();

        // Formula injection protection
        if (!str.isEmpty()) {
            char first = str.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@') {
                str = "'" + str;
            }
        }

        // Standard CSV quotes and commas escaping
        if (str.contains(",") || str.contains("\"") || str.contains("\n") || str.contains("\r")) {
            str = "\"" + str.replace("\"", "\"\"") + "\"";
        }

        return str;
    }

    /**
     * Converts a list of rows (each row is a list of cell values) into CSV text.
     */
    public static String toCsv(List<String> headers, List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder();

        // Header line
        for (int i = 0; i < headers.size(); i++) {
            sb.append(escapeCell(headers.get(i)));
            if (i < headers.size() - 1) sb.append(",");
        }
        sb.append("\r\n");

        // Data lines
        for (List<Object> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                sb.append(escapeCell(row.get(i)));
                if (i < row.size() - 1) sb.append(",");
            }
            sb.append("\r\n");
        }

        return sb.toString();
    }
}
