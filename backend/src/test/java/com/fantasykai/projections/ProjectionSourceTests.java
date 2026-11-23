package com.fantasykai.projections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fantasykai.projections.ProjectionSource.Measurement;
import com.fantasykai.projections.ProjectionSource.TypicalError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * A source id the database cannot hold is refused where the source is defined -- at startup,
 * for a provider's constant -- not by the first night's insert. The same goes for an error
 * figure that the measurements behind it no longer support.
 */
class ProjectionSourceTests {

    @Test
    void anIdOfExactlyTheMaximumLengthIsAccepted() {
        String id = "a".repeat(ProjectionSource.MAX_ID_LENGTH - 3) + "-v1";

        assertThat(new ProjectionSource(id, "Longest", "An id at the limit.").id())
                .hasSize(ProjectionSource.MAX_ID_LENGTH);
    }

    @Test
    void anIdOneCharacterLongerIsRefusedBeforeAnythingIsStored() {
        String id = "a".repeat(ProjectionSource.MAX_ID_LENGTH - 2) + "-v1";

        assertThatThrownBy(() -> new ProjectionSource(id, "Too long", "An id past the limit."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most " + ProjectionSource.MAX_ID_LENGTH)
                .hasMessageContaining(String.valueOf(ProjectionSource.MAX_ID_LENGTH + 1));
    }

    @Test
    void theShippedIdIsWellInside() {
        assertThat(WeightedAverageProjectionProvider.SOURCE.id().length())
                .isLessThanOrEqualTo(ProjectionSource.MAX_ID_LENGTH);
    }

    @Test
    void aSourceNeedsALabelAndASummary() {
        assertThatThrownBy(() -> new ProjectionSource("a-v1", " ", "A summary."))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("label and a summary");
        assertThatThrownBy(() -> new ProjectionSource("a-v1", "A label", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("label and a summary");
    }

    // ------------------------------------------------------------------ the error figure

    /** The headline may sit exactly 0.2 from a measurement and no further. */
    @Test
    void aHeadlineIsHeldToWithinPointTwoOfEveryMeasurement() {
        assertThatCode(() -> new TypicalError("PPR MAE", 5.5, "players who appeared",
                List.of(new Measurement(2024, null, 5.3, null), new Measurement(2025, null, 5.7, null))))
                .as("0.2 either side is the edge, and inside").doesNotThrowAnyException();

        assertThatThrownBy(() -> new TypicalError("PPR MAE", 5.5, "players who appeared",
                List.of(new Measurement(2024, null, 5.5, null), new Measurement(2026, "1-4", 5.71, 896))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5.5").hasMessageContaining("5.71").hasMessageContaining("2026 weeks 1-4");
        assertThatThrownBy(() -> new TypicalError("PPR MAE", 5.5, "players who appeared",
                List.of(new Measurement(2024, null, 5.29, null))))
                .as("the other side of the edge").isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aTypicalErrorNeedsItsMetricItsPopulationAndAMeasurement() {
        List<Measurement> one = List.of(new Measurement(2025, null, 5.5, 3807));
        assertThatThrownBy(() -> new TypicalError(" ", 5.5, "players who appeared", one))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TypicalError("PPR MAE", 5.5, null, one))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TypicalError("PPR MAE", 5.5, "players who appeared", List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at least one measurement");
        assertThatThrownBy(() -> new Measurement(2025, null, 0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Measurement(2025, null, Double.NaN, null))
                .isInstanceOf(IllegalArgumentException.class);
        // A headline that is not a number is not "within 0.2" of anything: NaN fails every comparison,
        // so without its own check it would sail through the tolerance test and print as "about NaN".
        for (double notAHeadline : new double[] {Double.NaN, Double.POSITIVE_INFINITY, 0, -5.5}) {
            assertThatThrownBy(() -> new TypicalError("PPR MAE", notAHeadline, "players who played", one))
                    .as("headline %s", notAHeadline).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive number");
        }
    }

    /**
     * The shipped figures are the committed reports', row by row -- not a number that merely looks
     * right. A figure edited without its report, or a report re-run without the figure, fails here.
     * The reports are read from the repository (tests run in {@code backend/}); a missing file is a
     * failure, not a skip.
     */
    @Test
    void theShippedErrorFiguresAreTheCommittedReportsRows() throws IOException {
        TypicalError error = WeightedAverageProjectionProvider.SOURCE.typicalError();
        String accuracy = report("docs/perf/projection-accuracy.md");
        String tuning = report("docs/perf/projection-backtest/validate-2024.md");
        String test2025 = report("docs/perf/projection-backtest/test-2025.md");
        String prospective = report("docs/perf/projection-backtest/v2-prospective-2026.md");

        assertThat(error.measured()).as("exactly the three measurements D3 approved").containsExactly(
                new Measurement(2024, null, 5.480, 3808),
                new Measurement(2025, null, 5.512, 3807),
                new Measurement(2026, "1-4", 5.688, 896));
        // 2024, the tuning season: the baseline's row of the generated report, and the sample's size.
        assertThat(tuning).containsPattern(Pattern.compile("(?m)^\\| D EWMA h=4(?: \\| [-+0-9.]+){2} \\| "
                + Pattern.quote(format(error.measured().get(0).mae())) + " \\|"));
        assertThat(tuning).containsPattern(Pattern.compile("(?m)^\\| 2024 \\| 18 \\| 3,808 \\|"));
        // 2025, the test season: the baseline's row of the results table, and the population's size.
        assertThat(accuracy).containsPattern(Pattern.compile("(?m)^\\| \\*\\*D, EWMA h=4 \\(best baseline\\)\\*\\*"
                + "(?: \\| [-+0-9.]+){2} \\| " + Pattern.quote(format(error.measured().get(1).mae())) + " \\|"));
        assertThat(accuracy).contains("Population P: 3,807");
        // 2026 weeks 1-4, official population P: the baseline's row, and the sample's size.
        assertThat(prospective).containsPattern(Pattern.compile("(?m)^\\| D EWMA h=4(?: \\| [-+0-9.]+){2} \\| "
                + Pattern.quote(format(error.measured().get(2).mae())) + " \\|"));
        assertThat(prospective).containsPattern(Pattern.compile("(?m)^\\| 2026 \\| 4 \\| 896 \\|"));
    }

    /**
     * The population is part of the claim: P is the research's fixed group of each week's most-used
     * players who played, not the board's. What the shipped text says about it, and the board-wide
     * figures its Javadoc sets beside it, are the reports' words and numbers.
     */
    @Test
    void thePopulationIsTheReportsAndTheBoardWideFiguresAreTheReportsToo() throws IOException {
        String population = WeightedAverageProjectionProvider.SOURCE.typicalError().population();

        assertThat(population).contains("top 32 QB, 64 RB, 96 WR, 32 TE").contains("who played");
        assertThat(report("docs/perf/projection-accuracy.md")).as("the report's own definition of P")
                .contains("top QB 32 / RB 64 / WR 96 / TE 32").contains("P is conditional on");
        assertThat(report("docs/perf/projection-backtest/validate-2024.md")).as("2024 board-wide")
                .containsPattern(Pattern.compile("(?m)^\\| P-all \\(every player with history who played\\) \\| 5,746 \\| 4\\.509 \\|"));
        assertThat(report("docs/perf/projection-backtest/test-2025.md")).as("2025 board-wide")
                .containsPattern(Pattern.compile("(?m)^\\| P-all \\(every player with history who played\\) \\| 5,914 \\| 4\\.470 \\|"));
    }

    /** The copy says "about 5.5": that holds while the shipped measurements stay within 0.2 of it. */
    @Test
    void theShippedHeadlineSitsInsideTheToleranceOfEveryMeasurement() {
        TypicalError error = WeightedAverageProjectionProvider.SOURCE.typicalError();

        assertThat(error.metric()).isEqualTo("PPR MAE");
        assertThat(error.value()).isEqualTo(5.5);
        assertThat(error.measured()).allSatisfy(m ->
                assertThat(Math.abs(error.value() - m.mae())).isLessThanOrEqualTo(TypicalError.HEADLINE_TOLERANCE));
    }

    // ------------------------------------------------------------------ the wire

    @Test
    void theSourceSerializesItsSummaryAndItsMeasurementsAndOmitsWhatIsNotKnown() {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();

        JsonNode shipped = json.valueToTree(WeightedAverageProjectionProvider.SOURCE);
        assertThat(names(shipped)).containsExactly("id", "label", "summary", "typicalError");
        assertThat(names(shipped.get("typicalError"))).containsExactly("metric", "value", "population", "measured");
        assertThat(names(shipped.get("typicalError").get("measured").get(0))).as("a full season: no weeks")
                .containsExactly("season", "mae", "n");
        assertThat(names(shipped.get("typicalError").get("measured").get(1))).containsExactly("season", "mae", "n");
        assertThat(names(shipped.get("typicalError").get("measured").get(2)))
                .containsExactly("season", "weeks", "mae", "n");
        // What a report does not state is left out of the JSON, not sent as null.
        JsonNode unstated = json.valueToTree(new Measurement(2025, null, 5.5, null));
        assertThat(names(unstated)).containsExactly("season", "mae");

        JsonNode bare = json.valueToTree(new ProjectionSource("bare-v1", "Bare", "No published error."));
        assertThat(bare.get("typicalError").isNull()).as("a source with no figure says so").isTrue();
    }

    private static String format(double mae) {
        return String.format(java.util.Locale.ROOT, "%.3f", mae);
    }

    private static String report(String fromRepoRoot) throws IOException {
        return Files.readString(Path.of("..").resolve(fromRepoRoot));
    }

    private static List<String> names(JsonNode node) {
        List<String> names = new java.util.ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
