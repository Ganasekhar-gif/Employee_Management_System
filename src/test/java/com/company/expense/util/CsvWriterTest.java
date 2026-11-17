package com.company.expense.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CsvWriterTest {

    @Test
    void writesPlainValuesAndNulls() {
        String csv = new CsvWriter().row(Arrays.asList(1, "Asha", null, new BigDecimal("12.50"))).toString();

        assertThat(csv).isEqualTo("1,Asha,,12.50\r\n");
    }

    @Test
    void quotesCommasQuotesAndNewlines() {
        assertThat(CsvWriter.escape("Pune, MH")).isEqualTo("\"Pune, MH\"");
        assertThat(CsvWriter.escape("6\" ruler")).isEqualTo("\"6\"\" ruler\"");
        assertThat(CsvWriter.escape("line1\nline2")).isEqualTo("\"line1\nline2\"");
    }

    @Test
    void neutralisesSpreadsheetFormulas() {
        assertThat(CsvWriter.escape("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(CsvWriter.escape("+91 call")).isEqualTo("'+91 call");
        assertThat(CsvWriter.escape("@SUM(A1)")).isEqualTo("'@SUM(A1)");
    }

    @Test
    void leavesNegativeNumbersAlone() {
        assertThat(CsvWriter.escape(new BigDecimal("-5.00"))).isEqualTo("-5.00");
    }
}
