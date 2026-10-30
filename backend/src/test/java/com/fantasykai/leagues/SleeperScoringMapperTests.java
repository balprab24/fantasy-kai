package com.fantasykai.leagues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fantasykai.scoring.Bonus;
import com.fantasykai.scoring.RulesetValidator;
import com.fantasykai.scoring.StatKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SleeperScoringMapperTests {

    private final ObjectMapper json = new ObjectMapper();
    private final SleeperScoringMapper mapper = new SleeperScoringMapper(new RulesetValidator());

    private ObjectNode captured() {
        return (ObjectNode) new FixtureLeagueHttp(json).fixture("scoring_captured.json");
    }

    /** The captured league's 81 settings: exact keys copied, float noise rounded away. */
    @Test
    void theCapturedLeagueMapsExactlyWhereSleeperNamesOurStats() {
        LeagueScoring scoring = mapper.map(captured());

        assertThat(scoring.error()).isNull();
        assertThat(scoring.ruleset().base()).containsAllEntriesOf(Map.of(
                StatKey.PASS_YD, 0.04, StatKey.PASS_TD, 6.0, StatKey.PASS_INT, -2.0,
                StatKey.REC, 1.0, StatKey.REC_YD, 0.1, StatKey.RUSH_YD, 0.1,
                StatKey.FUM_LOST, -2.0, StatKey.REC_2PT, 2.0));
        assertThat(scoring.ruleset().bonuses()).as("every yardage bonus is 0 there").isEmpty();
        assertThat(scoring.ruleset().positionOverrides()).isEmpty();
    }

    /**
     * The acceptance test: an unmodelled key is reported, never dropped. Every
     * non-zero setting the ruleset does not carry is listed; zero ones are not,
     * because a setting worth nothing scores nothing either way.
     */
    @Test
    void everyNonZeroSettingTheRulesetDoesNotCarryIsReported() {
        LeagueScoring scoring = mapper.map(captured());

        assertThat(scoring.unmodeled()).containsKeys("fgm_0_19", "fgmiss_0_19", "xpm", "pts_allow_0",
                "def_td", "sack", "int", "ff", "blk_kick", "safe", "fum_rec", "st_ff", "st_fum_rec");
        assertThat(scoring.unmodeled()).doesNotContainKeys("pass_att", "fum", "kr_td", "idp_tkl");
        assertThat(scoring.unmodeled()).as("mapped keys are not also reported")
                .doesNotContainKeys("pass_yd", "rec", "fum_lost", "st_td");

        int nonZero = (int) captured().properties().stream()
                .filter(e -> e.getValue().asDouble() != 0.0).count();
        int carried = (int) scoring.ruleset().base().values().stream().filter(v -> v != 0.0).count();
        assertThat(scoring.unmodeled()).as("nothing non-zero falls between the two")
                .hasSize(nonZero - carried);
    }

    @Test
    void returnTouchdownsShareRetTdAndSaySo() {
        LeagueScoring scoring = mapper.map(captured()); // st_td 6, kr_td and pr_td 0

        assertThat(scoring.ruleset().base()).containsEntry(StatKey.RET_TD, 6.0);
        assertThat(scoring.approximations()).singleElement().asString().contains("return touchdowns", "6");
    }

    @Test
    void returnTouchdownsWorthDifferentAmountsAreLeftUnmodelledNotAveraged() {
        ObjectNode settings = captured().put("kr_td", 4.0); // st_td stays 6

        LeagueScoring scoring = mapper.map(settings);

        assertThat(scoring.ruleset().base()).doesNotContainKey(StatKey.RET_TD);
        assertThat(scoring.unmodeled()).containsEntry("kr_td", 4.0).containsEntry("st_td", 6.0);
        assertThat(scoring.approximations()).isEmpty();
    }

    @Test
    void yardageBonusesBecomeThresholdBonuses() {
        ObjectNode settings = captured().put("bonus_rush_yd_100", 3.0).put("bonus_pass_yd_400", 2.0);

        assertThat(mapper.map(settings).ruleset().bonuses()).containsExactly(
                new Bonus(StatKey.PASS_YD, 400, 2.0), new Bonus(StatKey.RUSH_YD, 100, 3.0));
    }

    @Test
    void aPositionReceptionPremiumOverridesRecForThatPositionOnly() {
        ObjectNode settings = captured().put("bonus_rec_te", 0.5);

        LeagueScoring scoring = mapper.map(settings);

        assertThat(scoring.ruleset().positionOverrides())
                .containsExactly(Map.entry("TE", Map.of(StatKey.REC, 1.5)));
        assertThat(scoring.ruleset().base()).containsEntry(StatKey.REC, 1.0);
    }

    /** RulesetValidator: rejections are loud. The league still gets told why. */
    @Test
    void scoringOutsideTheValidatorsBoundsIsRefusedNotClamped() {
        ObjectNode settings = captured().put("pass_td", 50.0);

        LeagueScoring scoring = mapper.map(settings);

        assertThat(scoring.ruleset()).isNull();
        assertThat(scoring.error()).contains("pass_td", "50");
        assertThat(scoring.unmodeled()).as("still reported").containsKey("fgm_0_19");
    }

    @Test
    void aSettingThatIsNotANumberIsAnErrorNotAZero() {
        ObjectNode settings = captured().put("pass_td", "six");

        assertThatThrownBy(() -> mapper.map(settings))
                .isInstanceOf(LeagueDataException.class).hasMessageContaining("pass_td");
    }

    @Test
    void roundingIsDecimalHalfUpToFourPlaces() {
        assertThat(SleeperScoringMapper.round(0.03999999910593033)).isEqualTo(0.04);
        assertThat(SleeperScoringMapper.round(0.10000000149011612)).isEqualTo(0.1);
        assertThat(SleeperScoringMapper.round(0.00005)).isEqualTo(0.0001);
        assertThat(SleeperScoringMapper.round(-0.00005)).isEqualTo(-0.0001);
    }

    @Test
    void anUnknownKeyIsReportedUnderItsOwnName() {
        ObjectNode settings = captured().put("brand_new_stat", 1.25);

        LeagueScoring scoring = mapper.map(settings);

        assertThat(scoring.unmodeled()).containsEntry("brand_new_stat", 1.25);
        assertThat(Unmodeled.describe(scoring.unmodeled(), Map.of()))
                .anySatisfy(item -> {
                    assertThat(item.key()).isEqualTo("brand_new_stat");
                    assertThat(item.label()).isEqualTo("brand_new_stat");
                    assertThat(item.group()).isEqualTo(Unmodeled.Group.OFFENSE);
                });
    }

    @Test
    void unmodelledKeysAreGroupedForTheMember() {
        assertThat(Unmodeled.group("fgm_40_49")).isEqualTo(Unmodeled.Group.KICKING);
        assertThat(Unmodeled.group("xpmiss")).isEqualTo(Unmodeled.Group.KICKING);
        assertThat(Unmodeled.group("fg_ret_yd")).isEqualTo(Unmodeled.Group.TEAM_DEFENSE);
        assertThat(Unmodeled.group("pts_allow_0")).isEqualTo(Unmodeled.Group.TEAM_DEFENSE);
        assertThat(Unmodeled.group("idp_tkl")).isEqualTo(Unmodeled.Group.DEFENSIVE_PLAYERS);
        assertThat(Unmodeled.group("pass_att")).isEqualTo(Unmodeled.Group.OFFENSE);
    }
}
