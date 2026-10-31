package com.fantasykai.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fantasykai.projections.AvailabilityService;
import com.fantasykai.projections.ProjectionDatabase;
import com.fantasykai.projections.ProjectionProperties;
import com.fantasykai.projections.ProjectionRepository;
import com.fantasykai.projections.RawProjection;
import com.fantasykai.projections.SeasonWeek;
import com.fantasykai.projections.WeightedAverageProjectionProvider;
import com.fantasykai.scoring.ResolvedRuleset;
import com.fantasykai.scoring.Ruleset;
import com.fantasykai.scoring.ScoringProfiles;
import com.fantasykai.scoring.StatKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProjectionOrderingTests {
    @Test void sortsRawPointsBeforePagingAndKeepsRawValueOutOfJson() {
        var db = ProjectionDatabase.get();
        db.reset();
        int home = db.team("BUF"), away = db.team("NYJ");
        long low = db.player("Lower", "WR", home), high = db.player("Higher", "WR", home);
        Instant now = Instant.parse("2030-09-01T00:00:00Z");
        long game = db.game(2030, 1, home, away, now.plusSeconds(86400));
        var week = new SeasonWeek(2030, 1);
        var provider = new WeightedAverageProjectionProvider();
        var rows = new java.util.ArrayList<ProjectionRepository.ProjectedRow>();
        for (long id : List.of(low, high)) {
            double[] stats = new double[StatKey.COUNT];
            stats[StatKey.REC_YD.index()] = id == low ? 100 : 100.04;
            rows.add(new ProjectionRepository.ProjectedRow(id, game, home,
                    new RawProjection(provider.source(), week, stats, 0, 0, 0, 4, new SeasonWeek(2029, 18))));
        }
        new ProjectionRepository(db.jdbc).replaceOpen(week, provider.source().id(), rows, now);
        var profiles = mock(ScoringProfiles.class);
        when(profiles.byId(1L, 7L)).thenReturn(ResolvedRuleset.compile(
                new Ruleset(1, Map.of(StatKey.REC_YD, 0.1), Map.of(), List.of())));
        var availability = mock(AvailabilityService.class);
        when(availability.forWeek(week)).thenReturn(Map.of());
        var controller = new ProjectionController(db.jdbc, profiles, availability,
                new ProjectionProperties(provider.source().id(), false), provider,
                Clock.fixed(now, ZoneOffset.UTC));
        var first = controller.board(1, null, "", 0, 1, 7L).page().content().getFirst();
        var second = controller.board(1, null, "", 1, 1, 7L).page().content().getFirst();
        assertThat(first.playerId()).isEqualTo(high);
        assertThat(second.playerId()).isEqualTo(low);
        assertThat(first.points()).isEqualTo(second.points()).isEqualTo(10.0);
        var json = new ObjectMapper().findAndRegisterModules().<com.fasterxml.jackson.databind.JsonNode>valueToTree(first);
        assertThat(json.has("rawPoints")).isFalse();
        // The explanation's wire shape, unchanged since it moved into scoring.ExplainedScore.
        assertThat(json.get("parts")).hasSize(1);
        assertThat(json.get("parts").get(0).toString())
                .isEqualTo("{\"stat\":\"rec_yd\",\"value\":100.04,\"rate\":0.1,\"points\":" + (100.04 * 0.1) + "}");
        assertThat(json.get("bonusPoints").asDouble()).isEqualTo(0.0);
        assertThat(controller.player(high, 1, 7L).projection().points()).isEqualTo(first.points());
    }
}
