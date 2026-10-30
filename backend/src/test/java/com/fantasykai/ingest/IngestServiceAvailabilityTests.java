package com.fantasykai.ingest;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Availability is current information: the roster and the injury report are read for the season
 * being played only -- by the daily pull and by a backfill of every season alike -- and after the
 * player master their rows are tied to. Plain JUnit with mocks; nothing is fetched.
 */
class IngestServiceAvailabilityTests {

    /** October 2026: the 2026 season. */
    private static final Clock OCTOBER =
            Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), ZoneId.of("America/New_York"));

    private final TeamIngestor teams = mock(TeamIngestor.class);
    private final PlayerIngestor players = mock(PlayerIngestor.class);
    private final GameIngestor games = mock(GameIngestor.class);
    private final StatIngestor stats = mock(StatIngestor.class);
    private final SnapCountIngestor snaps = mock(SnapCountIngestor.class);
    private final RosterWeekIngestor rosters = mock(RosterWeekIngestor.class);
    private final InjuryReportIngestor injuries = mock(InjuryReportIngestor.class);
    private final SleeperCrosswalk sleeper = mock(SleeperCrosswalk.class);

    private IngestService service;

    @BeforeEach
    void wire() {
        IngestResult nothing = new IngestResult("test", 0, 0, 0);
        when(teams.ingest()).thenReturn(nothing);
        when(players.ingest()).thenReturn(nothing);
        when(games.ingest(anyList())).thenReturn(nothing);
        when(stats.ingest(anyInt())).thenReturn(nothing);
        when(snaps.ingest(anyInt())).thenReturn(nothing);
        when(rosters.ingest(anyInt())).thenReturn(nothing);
        when(injuries.ingest(anyInt())).thenReturn(nothing);
        when(sleeper.ingest()).thenReturn(nothing);
        service = new IngestService(teams, players, games, stats, snaps, rosters, injuries, sleeper,
                mock(IntegrityChecks.class), mock(IngestRunRecorder.class),
                new IngestProperties("https://example.invalid", "https://example.invalid", 2020,
                        "0 0 6 * * *", false, false, false),
                OCTOBER);
    }

    @Test
    void aBackfillOfEverySeasonReadsAvailabilityForTheCurrentOneOnly() {
        service.backfill();

        verify(stats, times(7)).ingest(anyInt());
        verify(rosters, times(1)).ingest(2026);
        verify(injuries, times(1)).ingest(2026);
        verify(rosters, never()).ingest(2025);
    }

    @Test
    void theDailyPullReadsTheCurrentSeasonsAvailability() {
        service.ingestCurrentSeason();

        verify(rosters).ingest(2026);
        verify(injuries).ingest(2026);
    }
}
