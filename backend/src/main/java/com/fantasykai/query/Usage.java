package com.fantasykai.query;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Volume: how often a player was given the ball, as opposed to what he did with it.
 *
 * <p>Deliberately <em>not</em> {@link com.fantasykai.scoring.StatKey}s. No v1
 * ruleset scores an attempt or a target, and putting them in the stat map would
 * make them look scorable to every reader of the API -- and to the validator, the
 * day someone adds one "for display". They are named here and nowhere else: this
 * select list and this reader are the only places the four column names appear
 * on the read path.
 *
 * @param passAtt  pass attempts
 * @param passCmp  completions
 * @param rushAtt  carries
 * @param targets  times thrown to
 */
public record Usage(int passAtt, int passCmp, int rushAtt, int targets) {

    public static final Usage NONE = new Usage(0, 0, 0, 0);

    /** {@code "s.pass_att, s.pass_cmp, s.rush_att, s.targets"}. */
    public static final String SELECT_LIST = "s.pass_att, s.pass_cmp, s.rush_att, s.targets";

    static Usage read(ResultSet rs) throws SQLException {
        return new Usage(rs.getInt("pass_att"), rs.getInt("pass_cmp"),
                rs.getInt("rush_att"), rs.getInt("targets"));
    }

    public Usage plus(Usage other) {
        return new Usage(passAtt + other.passAtt, passCmp + other.passCmp,
                rushAtt + other.rushAtt, targets + other.targets);
    }
}
