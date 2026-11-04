package com.fantasykai.leagues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fantasykai.leagues.RestOfSeason.Outlook;
import com.fantasykai.projections.AvailabilityService;
import com.fantasykai.projections.AvailabilityStatus;
import com.fantasykai.projections.HistoricalGame;
import com.fantasykai.projections.JdbcProjectionInputs;
import com.fantasykai.projections.ProjectionDatabase;
import com.fantasykai.projections.SeasonWeek;
import com.fantasykai.projections.WeightedAverageProjectionProvider;
import com.fantasykai.scoring.ResolvedRuleset;
import com.fantasykai.scoring.Ruleset;
import com.fantasykai.scoring.StatKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Rest-of-season expected points against a real schema: games decide the weeks and the byes,
 * rosters and the injury report decide who plays, and the provider's line decides how much.
 */
class RestOfSeasonTests {

    /** 2030: far from any real season, so nothing loaded locally can leak in. */
    private static final int SEASON = 2030;
    /** Week 3 kicks off after this; weeks 1-2 are history. */
    private static final Instant NOW = Instant.parse("2030-09-20T12:00:00Z");
    private static final ResolvedRuleset REC_YD = ResolvedRuleset.compile(
            new Ruleset(1, Map.of(StatKey.REC_YD, 0.1, StatKey.REC, 1.0), Map.of(), List.of()));

    private final ProjectionDatabase db = ProjectionDatabase.get();
    private RestOfSeason restOfSeason;
    private int buf;
    private int nyj;
    private int mia;

    @BeforeEach
    void seed() {
        db.reset();
        buf = db.team("BUF");
        nyj = db.team("NYJ");
        mia = db.team("MIA");
        restOfSeason = new RestOfSeason(db.jdbc, new JdbcProjectionInputs(db.jdbc),
                new WeightedAverageProjectionProvider(), new AvailabilityService(db.jdbc),
                Clock.fixed(NOW, ZoneOffset.UTC));
        // Weeks 1-6. BUF plays every week but week 5 (its bye); NYJ every week; MIA fills in.
        for (int week = 1; week <= 6; week++) {
            Instant kickoff = Instant.parse("2030-09-08T17:00:00Z").plusSeconds(7L * 86400 * (week - 1));
            if (week != 5) {
                db.game(SEASON, week, buf, week == 3 ? mia : nyj, kickoff);
            } else {
                db.game(SEASON, week, nyj, mia, kickoff);
            }
        }
    }

    private long receiver(String name, int team) {
        long id = db.player(name, "WR", team);
        for (int week = 1; week <= 2; week++) {
            long game = db.jdbc.queryForObject("SELECT id FROM games WHERE season = ? AND week = ? "
                    + "AND (home_team_id = ? OR away_team_id = ?)", Long.class, SEASON, week, team, team);
            double[] stats = new double[StatKey.COUNT];
            stats[StatKey.REC.index()] = 5;
            stats[StatKey.REC_YD.index()] = 60;
            db.stats(id, game, team, new HistoricalGame(new SeasonWeek(SEASON, week), stats, 0, 0, 7));
        }
        return id;
    }

    @Test
    void weeksRunFromTheFirstUnplayedWeekToThePlayoffs() {
        assertThat(restOfSeason.weeks(SEASON, 6)).containsExactly(3, 4, 5);
        assertThat(restOfSeason.weeks(SEASON, 0)).as("no playoffs: to week 18").startsWith(3).endsWith(18);
        assertThat(restOfSeason.weeks(2031, 15)).as("no schedule yet").isEmpty();
    }

    /** 5 catches and 60 yards a game under 1 + 0.1/yd: 11 a week, 0 on the bye. */
    @Test
    void aPlayerScoresHisLineEveryWeekExceptHisBye() {
        long player = receiver("Steady Receiver", buf);

        Outlook outlook = restOfSeason.project(SEASON, 7, Map.of(player, "WR"), REC_YD).players().get(player);

        assertThat(outlook.status()).isEqualTo(RestOfSeason.Status.PROJECTED);
        assertThat(outlook.perGame().total()).isCloseTo(11.0, within(1e-9));
        assertThat(outlook.weekly()).containsExactly(Map.entry(3, 11.0), Map.entry(4, 11.0),
                Map.entry(5, 0.0), Map.entry(6, 11.0));
        assertThat(outlook.mean()).isCloseTo(8.25, within(1e-9));
    }

    @Test
    void outMissesTheFirstWeekOnly_reserveMissesThemAll() {
        long out = receiver("Out This Week", buf);
        long reserve = receiver("On Injured Reserve", nyj);
        db.roster(SEASON, 3, out, buf, "ACT");
        db.injury(SEASON, 3, out, buf, "Out");
        db.roster(SEASON, 3, reserve, nyj, "RES");

        var players = restOfSeason.project(SEASON, 7, Map.of(out, "WR", reserve, "WR"), REC_YD).players();

        assertThat(players.get(out).availability()).isEqualTo(AvailabilityStatus.OUT);
        assertThat(players.get(out).weekly()).containsExactly(Map.entry(3, 0.0), Map.entry(4, 11.0),
                Map.entry(5, 0.0), Map.entry(6, 11.0));
        assertThat(players.get(reserve).weekly().values()).containsOnly(0.0);
    }

    @Test
    void questionableAndDoubtfulPlay_noProbabilityIsInvented() {
        assertThat(RestOfSeason.plays(3, 3, buf, AvailabilityStatus.QUESTIONABLE, Map.of(3, Set.of(buf)))).isTrue();
        assertThat(RestOfSeason.plays(3, 3, buf, AvailabilityStatus.DOUBTFUL, Map.of(3, Set.of(buf)))).isTrue();
        assertThat(RestOfSeason.plays(3, 3, null, AvailabilityStatus.AVAILABLE, Map.of(3, Set.of(buf))))
                .as("no team, no game").isFalse();
        assertThat(RestOfSeason.plays(4, 3, buf, AvailabilityStatus.NOT_ON_ROSTER, Map.of(4, Set.of(buf)))).isFalse();
    }

    @Test
    void aRookieWithNoGameIsFlagged_aKickerIsNotModelled() {
        long rookie = db.player("First Snap Pending", "RB", buf);
        long kicker = db.player("Kicker", "K", buf);

        var players = restOfSeason.project(SEASON, 7, Map.of(rookie, "RB", kicker, "K"), REC_YD).players();

        assertThat(players.get(rookie).status()).isEqualTo(RestOfSeason.Status.NO_PROJECTION);
        assertThat(players.get(kicker).status()).isEqualTo(RestOfSeason.Status.NOT_MODELED);
        assertThat(players.get(rookie).weekly().values()).containsOnly(0.0);
    }

    /** A week-3 stat row (a Thursday game, say) is not history for week 3's projection. */
    @Test
    void theFirstRemainingWeekIsNeverItsOwnHistory() {
        long player = receiver("Thursday Receiver", buf);
        long week3 = db.jdbc.queryForObject("SELECT id FROM games WHERE season = ? AND week = 3", Long.class, SEASON);
        double[] big = new double[StatKey.COUNT];
        big[StatKey.REC.index()] = 15;
        big[StatKey.REC_YD.index()] = 250;
        db.stats(player, week3, buf, new HistoricalGame(new SeasonWeek(SEASON, 3), big, 0, 0, 20));

        Outlook outlook = restOfSeason.project(SEASON, 7, Map.of(player, "WR"), REC_YD).players().get(player);

        assertThat(outlook.perGame().total()).isCloseTo(11.0, within(1e-9));
    }
}
