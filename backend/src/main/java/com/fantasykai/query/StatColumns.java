package com.fantasykai.query;

import com.fantasykai.scoring.StatKey;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The scorable-stat half of a query, generated from {@link StatKey}.
 *
 * <p>{@code StatKey.json()} is simultaneously the ruleset key, the dot-product
 * array index and the {@code player_game_stats} column name, so the projection
 * list is derived from the enum rather than maintained beside it. Add a stat to
 * the enum and this query grows a column; there is no second place to forget.
 *
 * <p>This is also the one sanctioned piece of generated SQL under §8. The
 * strings are enum constants fixed at compile time -- no request parameter
 * reaches them -- which is what separates it from the string concatenation the
 * invariant forbids. Every <em>value</em> is still bound with {@code ?}.
 */
public final class StatColumns {

    /** {@code "s.pass_yd, s.pass_td, ..."} -- all 13 scorable stats, in enum order. */
    public static final String SELECT_LIST = Arrays.stream(StatKey.values())
            .map(stat -> "s." + stat.json())
            .collect(Collectors.joining(", "));

    private StatColumns() {}

    /**
     * Reads one row into the {@code double[]} the evaluator dot-products.
     *
     * <p>Indexed by {@link StatKey#index()} rather than by column position, so a
     * change to {@link #SELECT_LIST} ordering cannot silently misalign a stat
     * against the wrong rate. {@code getDouble} sidesteps pgjdbc handing back
     * {@code Integer} for {@code SMALLINT} and {@code BigDecimal} for
     * {@code NUMERIC}.
     */
    public static double[] readValues(ResultSet rs) throws SQLException {
        double[] values = new double[StatKey.COUNT];
        for (StatKey stat : StatKey.values()) {
            values[stat.index()] = rs.getDouble(stat.json());
        }
        return values;
    }
}
