package com.company.expense.util;

import java.util.List;
import java.util.stream.Collectors;

public final class CsvWriter {

    private static final String LINE_SEPARATOR = "\r\n";

    private final StringBuilder content = new StringBuilder();

    public CsvWriter row(List<?> values) {
        content.append(values.stream().map(CsvWriter::escape).collect(Collectors.joining(",")))
                .append(LINE_SEPARATOR);
        return this;
    }

    @Override
    public String toString() {
        return content.toString();
    }

    // Spreadsheets run cells starting with = + - @ as formulas, so such text is prefixed with a quote.
    static String escape(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (value instanceof CharSequence && !text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
