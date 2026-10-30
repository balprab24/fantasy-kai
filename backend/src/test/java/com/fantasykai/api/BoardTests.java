package com.fantasykai.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fantasykai.query.PlayerQueryRepository;
import com.fantasykai.query.RankingScope;
import com.fantasykai.query.ScorableRow;
import com.fantasykai.query.ScoringPosition;
import com.fantasykai.query.Usage;
import com.fantasykai.scoring.ScoringProfiles;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** {@link BoardController}: ranks over the whole population, recent form, the flat JSON contract. */
class BoardTests {

    private static RankingRow qb(int rank, long id, int games, double points, double perGame) {
        return new RankingRow(rank, id, "QB " + id, "QB", "BUF", games, points, perGame, null);
    }

    @Test
    void ranksWholePopulationBeforePagingAndKeepsMissingWeeksNull() {
        RankingsService rankings = mock(RankingsService.class);
        PlayerQueryRepository players = mock(PlayerQueryRepository.class);
        ScoringProfiles profiles = mock(ScoringProfiles.class);
        when(rankings.rank(1L, 7L, null, 2025, RankingScope.SEASON, 0, Integer.MAX_VALUE))
                .thenReturn(PageResponse.of(List.of(qb(1, 1, 4, 80, 20), qb(2, 2, 4, 60, 15)), 0, 2, 2));
        when(players.latestRegularWeek(2025)).thenReturn(Optional.of(4));
        when(players.findScorableRows(2025, ScoringPosition.resolve(null), null))
                .thenReturn(List.<ScorableRow>of());

        PageResponse<BoardController.Row> page =
                new BoardController(rankings, players, profiles).board(1, 2025, null, "season", 1, 1, 7L);

        // Page two of one-row pages: the second quarterback is QB2 by both
        // counts, not QB1 of the rows on his page.
        assertThat(page.content()).hasSize(1);
        BoardController.Row row = page.content().getFirst();
        assertThat(row.posRank()).isEqualTo(2);
        assertThat(row.ppgPosRank()).isEqualTo(2);
        assertThat(row.recentWeeks()).hasSize(4).allMatch(week -> week.points() == null);
        assertThat(row.recentPpg()).isNull();
        assertThat(row.form()).isNull();
    }

    @Test
    void perGameRanksShareTiesAndLeaveTheUnqualifiedOut() {
        // The leader has 4 games, so 2 qualify. Two at 15.0 share QB2; the
        // next is QB4, not QB3; the one-game player is not judged at all.
        Map<Long, Integer> ranks = BoardController.perGameRanks(List.of(
                qb(1, 1, 4, 80, 20),
                qb(2, 2, 4, 60, 15),
                qb(3, 3, 2, 30, 15),
                qb(4, 4, 4, 40, 10),
                qb(5, 5, 1, 25, 25)));

        assertThat(ranks).containsExactlyInAnyOrderEntriesOf(Map.of(1L, 1, 2L, 2, 3L, 2, 4L, 4));
    }

    @Test
    void serializesFlatRankingContract() {
        BoardController.Row row = new BoardController.Row(
                new RankingRow(1, 2, "A", "QB", null, 2, 12, 6, null),
                1, 1, null, null, List.of(), 4, Usage.NONE);

        JsonNode json = new ObjectMapper().valueToTree(row);

        assertThat(json.get("playerId").asLong()).isEqualTo(2);
        assertThat(json.has("ranking")).isFalse();
    }
}
