package com.fantasykai.ingest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.apache.commons.csv.CSVRecord;

/**
 * Null-safe reads from an nflverse CSV. Missing values arrive as "" or "NA",
 * and a column can disappear entirely if upstream restructures a release -- so
 * every accessor tolerates an absent column rather than throwing. A stat that
 * cannot be read is zero, which is what "did not record this" means for a box
 * score.
 */
final class CsvValues {

    private CsvValues() {}

    private static String raw(CSVRecord record, String column) {
        if (!record.isMapped(column)) {
            return null;
        }
        String value = record.get(column);
        if (value == null || value.isBlank() || "NA".equals(value)) {
            return null;
        }
        return value.trim();
    }

    static String text(CSVRecord record, String column) {
        return raw(record, column);
    }

    /** Truncates to {@code max} characters so an oversized value cannot fail the insert. */
    static String text(CSVRecord record, String column, int max) {
        String value = raw(record, column);
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    /** Box-score counts. nflverse writes whole numbers as "12.0", so parse as decimal first. */
    static short shortValue(CSVRecord record, String column) {
        BigDecimal value = decimal(record, column);
        return value == null ? 0 : value.setScale(0, RoundingMode.HALF_UP).shortValueExact();
    }

    static Integer integer(CSVRecord record, String column) {
        BigDecimal value = decimal(record, column);
        return value == null ? null : value.intValue();
    }

    static BigDecimal decimal(CSVRecord record, String column) {
        String value = raw(record, column);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * A rate that arrives as a 0..1 fraction but is stored as a percentage.
     * snap_counts.offense_pct is a fraction; the UI and the column are percent.
     */
    static BigDecimal fractionAsPercent(CSVRecord record, String column) {
        BigDecimal value = decimal(record, column);
        return value == null ? null : value.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }
}
