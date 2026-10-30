package com.fantasykai.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The extras reader takes nflverse's definitions as verified, and never mistakes "could not
 * read it" for "it was zero" -- the {@code CsvValues.shortValue} trap.
 */
class NflverseExtrasTests {

    private static final String STATS_HEADER = "player_id,game_id,team,season_type,position,"
            + "receiving_air_yards,passing_air_yards,targets,attempts";
    private static final String SNAPS_HEADER = "game_id,game_type,season,week,pfr_player_id,"
            + "position,team,offense_snaps,offense_pct";

    @Test
    void teamPassingAirYardsSumEveryPasserAndNoTargetMeansZeroAirYards(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("stats_player_week_2024.csv"), String.join("\n", STATS_HEADER,
                "00-1,2024_01_A_B,A,REG,QB,NA,310,0,35",
                "00-2,2024_01_A_B,A,REG,WR,120,NA,9,0",
                "00-3,2024_01_A_B,A,REG,TE,NA,NA,0,0",
                "00-4,2024_01_A_B,A,REG,RB,NA,12,1,1",
                "00-5,2024_19_A_B,A,POST,WR,99,NA,5,0",
                ",2024_01_A_B,A,REG,WR,30,NA,3,0"));
        Files.writeString(dir.resolve("snap_counts_2024.csv"), SNAPS_HEADER + "\n");
        NflverseExtras extras = NflverseExtras.read(dir, 2024, 2024);

        assertThat(extras.teamPassAirYards).containsEntry(NflverseExtras.teamKey("2024_01_A_B", "A"), 322.0);
        assertThat(extras.stats.get(NflverseExtras.statKey("00-2", "2024_01_A_B")).airYards()).isEqualTo(120);
        assertThat(extras.stats.get(NflverseExtras.statKey("00-3", "2024_01_A_B")).airYards()).isZero();
        assertThat(Double.isNaN(extras.stats.get(NflverseExtras.statKey("00-4", "2024_01_A_B")).airYards()))
                .as("a target with no air yards is unknown, not zero").isTrue();
        assertThat(extras.statRowsMissingAirYards).isEqualTo(1);
        assertThat(extras.stats).doesNotContainKey(NflverseExtras.statKey("00-5", "2024_19_A_B"));
        assertThat(extras.files).hasSize(2).allMatch(line -> line.contains("sha256"));
    }

    @Test
    void anAppearanceIsASkillPlayerWithOffensiveSnapsInTheRegularSeason(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("stats_player_week_2024.csv"), STATS_HEADER + "\n");
        Files.writeString(dir.resolve("snap_counts_2024.csv"), String.join("\n", SNAPS_HEADER,
                "2024_01_A_B,REG,2024,1,AbcdXx00,TE,A,14,0.21",
                "2024_01_A_B,REG,2024,1,EfghXx00,TE,A,0,0",
                "2024_01_A_B,REG,2024,1,IjklXx00,T,A,60,0.95",
                "2024_19_A_B,DIV,2024,19,MnopXx00,WR,A,40,0.6"));
        NflverseExtras extras = NflverseExtras.read(dir, 2024, 2024);
        assertThat(extras.snaps).extracting(NflverseExtras.Snap::pfrId).containsExactly("AbcdXx00");
        assertThat(extras.snaps.get(0).offensePct()).isEqualTo(0.21);
    }

    @Test
    void aMissingFileOrColumnIsAnErrorNeverAnEmptySeason(@TempDir Path dir) throws IOException {
        assertThatThrownBy(() -> NflverseExtras.read(dir, 2024, 2024))
                .isInstanceOf(IOException.class).hasMessageContaining("not in the cache");
        Files.writeString(dir.resolve("stats_player_week_2024.csv"), "player_id,game_id\n");
        assertThatThrownBy(() -> NflverseExtras.read(dir, 2024, 2024))
                .isInstanceOf(IOException.class).hasMessageContaining("has no column");
    }
}
