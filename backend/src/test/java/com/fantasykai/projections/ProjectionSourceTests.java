package com.fantasykai.projections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fantasykai.projections.ProjectionSource.ErrorFigure;
import com.fantasykai.projections.ProjectionSource.FigureKind;
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

    private static ErrorFigure figure(double value, String population, List<Measurement> measured) {
        return new ErrorFigure(FigureKind.IF_HE_PLAYS, value, population, measured);
    }

    /** The headline may sit exactly 0.2 from a measurement and no further. */
    @Test
    void aHeadlineIsHeldToWithinPointTwoOfEveryMeasurement() {
        assertThatCode(() -> figure(5.5, "players who appeared",
                List.of(new Measurement(2024, null, 5.3, null), new Measurement(2025, null, 5.7, null))))
                .as("0.2 either side is the edge, and inside").doesNotThrowAnyException();

        assertThatThrownBy(() -> figure(5.5, "players who appeared",
                List.of(new Measurement(2024, null, 5.5, null), new Measurement(2026, "1-4", 5.71, 896))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5.5").hasMessageContaining("5.71").hasMessageContaining("2026 weeks 1-4");
        assertThatThrownBy(() -> figure(5.5, "players who appeared",
                List.of(new Measurement(2024, null, 5.29, null))))
                .as("the other side of the edge").isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aFigureNeedsItsKindItsPopulationAndAMeasurement() {
        List<Measurement> one = List.of(new Measurement(2025, null, 5.5, 3807));
        assertThatThrownBy(() -> new ErrorFigure(null, 5.5, "players who appeared", one))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("what it is about");
        assertThatThrownBy(() -> figure(5.5, null, one)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> figure(5.5, " ", one)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> figure(5.5, "players who appeared", List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at least one measurement");
        assertThatThrownBy(() -> new Measurement(2025, null, 0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Measurement(2025, null, Double.NaN, null))
                .isInstanceOf(IllegalArgumentException.class);
        // A headline that is not a number is not "within 0.2" of anything: NaN fails every comparison,
        // so without its own check it would sail through the tolerance test and print as "about NaN".
        for (double notAHeadline : new double[] {Double.NaN, Double.POSITIVE_INFINITY, 0, -5.5}) {
            assertThatThrownBy(() -> figure(notAHeadline, "players who played", one))
                    .as("headline %s", notAHeadline).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive number");
        }
    }

    @Test
    void aTypicalErrorNeedsItsMetricAndOneFigureOfEachKindAtMost() {
        ErrorFigure plays = figure(5.5, "players who played", List.of(new Measurement(2025, null, 5.5, 3807)));
        ErrorFigure sat = new ErrorFigure(FigureKind.BEFORE_KICKOFF, 5.9, "the same, sitters as zeros",
                List.of(new Measurement(2025, null, 5.887, 3808)));

        assertThatThrownBy(() -> new TypicalError(" ", List.of(plays))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TypicalError("PPR MAE", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TypicalError("PPR MAE", List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at least one figure");
        assertThatThrownBy(() -> new TypicalError("PPR MAE", List.of(plays, plays)))
                .as("two of one kind would give the popover two answers to one question")
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("one figure of each kind");
        assertThat(new TypicalError("PPR MAE", List.of(plays, sat)).figures()).containsExactly(plays, sat);
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
        ErrorFigure plays = figureOf(error, FigureKind.IF_HE_PLAYS);

        assertThat(plays.measured()).as("exactly the three measurements D3 approved").containsExactly(
                new Measurement(2024, null, 5.480, 3808),
                new Measurement(2025, null, 5.512, 3807),
                new Measurement(2026, "1-4", 5.688, 896));
        // 2024, the tuning season: the baseline's row of the generated report, and the sample's size.
        assertThat(tuning).containsPattern(Pattern.compile("(?m)^\\| D EWMA h=4(?: \\| [-+0-9.]+){2} \\| "
                + Pattern.quote(format(plays.measured().get(0).mae())) + " \\|"));
        assertThat(tuning).containsPattern(Pattern.compile("(?m)^\\| 2024 \\| 18 \\| 3,808 \\|"));
        // 2025, the test season: the baseline's row of the results table, and the population's size.
        assertThat(accuracy).containsPattern(Pattern.compile("(?m)^\\| \\*\\*D, EWMA h=4 \\(best baseline\\)\\*\\*"
                + "(?: \\| [-+0-9.]+){2} \\| " + Pattern.quote(format(plays.measured().get(1).mae())) + " \\|"));
        assertThat(accuracy).contains("Population P: 3,807");
        // 2026 weeks 1-4, official population P: the baseline's row, and the sample's size.
        assertThat(prospective).containsPattern(Pattern.compile("(?m)^\\| D EWMA h=4(?: \\| [-+0-9.]+){2} \\| "
                + Pattern.quote(format(plays.measured().get(2).mae())) + " \\|"));
        assertThat(prospective).containsPattern(Pattern.compile("(?m)^\\| 2026 \\| 4 \\| 896 \\|"));
    }

    /**
     * The second figure is the research's P0: the same players, each who then did not play counted
     * as a zero. Each measurement is a row of the report it names, with its sample size, so the
     * figure cannot be edited without the report or the report re-run without the figure. The 2026
     * one lives in the availability table, which the report itself defines as P0, not in the table
     * the first figure's row comes from.
     */
    @Test
    void theBeforeKickoffFigureIsTheReportsP0RowsAndItsHeadlineIsTheirRounding() throws IOException {
        ErrorFigure sat = figureOf(WeightedAverageProjectionProvider.SOURCE.typicalError(), FigureKind.BEFORE_KICKOFF);

        assertThat(sat.measured()).containsExactly(
                new Measurement(2024, null, 5.845, 3808),
                new Measurement(2025, null, 5.887, 3808),
                new Measurement(2026, "1-4", 5.934, 896));
        assertThat(sat.value()).as("about 5.9: within 0.2 of all three, and what the popover says").isEqualTo(5.9);
        assertThat(report("docs/perf/projection-backtest/validate-2024.md")).as("2024")
                .containsPattern(Pattern.compile("(?m)^\\| P0 \\(played team's last game; DNP = 0\\) \\| 3,808 \\| "
                        + Pattern.quote(format(sat.measured().get(0).mae())) + " \\|"));
        assertThat(report("docs/perf/projection-backtest/test-2025.md")).as("2025")
                .containsPattern(Pattern.compile("(?m)^\\| P0 \\(played team's last game; DNP = 0\\) \\| 3,808 \\| "
                        + Pattern.quote(format(sat.measured().get(1).mae())) + " \\|"));
        String prospective = report("docs/perf/projection-backtest/v2-prospective-2026.md");
        assertThat(prospective).as("2026: the availability table's baseline row, all projected")
                .containsPattern(Pattern.compile("(?m)^\\| D EWMA h=4 \\| 896 / 792 / 104 \\| "
                        + Pattern.quote(format(sat.measured().get(2).mae())) + " \\|"));
        assertThat(prospective).as("...which the report defines as P0").contains("All projected = P0")
                .containsPattern(Pattern.compile("(?m)^\\| 2026 \\| 4 \\| 896 \\| 33 \\| 896 \\(104 inactive\\) \\|"));
    }

    /**
     * The population is part of the claim: P is the research's fixed group of each week's most-used
     * players who played, not the board's. What the shipped text says about it, and the board-wide
     * figures its Javadoc sets beside it, are the reports' words and numbers.
     */
    @Test
    void thePopulationIsTheReportsAndTheBoardWideFiguresAreTheReportsToo() throws IOException {
        TypicalError error = WeightedAverageProjectionProvider.SOURCE.typicalError();
        String population = figureOf(error, FigureKind.IF_HE_PLAYS).population();

        assertThat(figureOf(error, FigureKind.BEFORE_KICKOFF).population())
                .as("says who the zeros are, in the report's words")
                .contains("did not play as a zero").contains("played their team's previous game");
        assertThat(report("docs/perf/projection-backtest/v2-prospective-2026.md"))
                .contains("who played their team's previous game, before kickoff");
        assertThat(population).contains("top 32 QB, 64 RB, 96 WR, 32 TE").contains("who played");
        assertThat(report("docs/perf/projection-accuracy.md")).as("the report's own definition of P")
                .contains("top QB 32 / RB 64 / WR 96 / TE 32").contains("P is conditional on");
        assertThat(report("docs/perf/projection-backtest/validate-2024.md")).as("2024 board-wide")
                .containsPattern(Pattern.compile("(?m)^\\| P-all \\(every player with history who played\\) \\| 5,746 \\| 4\\.509 \\|"));
        assertThat(report("docs/perf/projection-backtest/test-2025.md")).as("2025 board-wide")
                .containsPattern(Pattern.compile("(?m)^\\| P-all \\(every player with history who played\\) \\| 5,914 \\| 4\\.470 \\|"));
        // ...and with players who then sat counted as zeros (P0): the "5.8 to 5.9" the Javadoc and the
        // architecture doc quote.
        assertThat(report("docs/perf/projection-backtest/validate-2024.md")).as("2024 P0")
                .containsPattern(Pattern.compile("(?m)^\\| P0 \\(played team's last game; DNP = 0\\) \\| 3,808 \\| 5\\.845 \\|"));
        assertThat(report("docs/perf/projection-backtest/test-2025.md")).as("2025 P0")
                .containsPattern(Pattern.compile("(?m)^\\| P0 \\(played team's last game; DNP = 0\\) \\| 3,808 \\| 5\\.887 \\|"));
    }

    /**
     * The copy says "about 5.5" under full PPR. That the headline sits within 0.2 of every
     * measurement is enforced where the figure is built (and fails class initialisation, so every
     * test that touches the source), so it is not re-asserted here; this pins what the figure is
     * called and says.
     */
    @Test
    void theShippedHeadlinesAreFivePointFiveAndFivePointNineInFullPpr() {
        TypicalError error = WeightedAverageProjectionProvider.SOURCE.typicalError();

        assertThat(error.metric()).isEqualTo("PPR MAE");
        assertThat(error.figures().stream().map(ErrorFigure::kind))
                .as("the order the popover reads them in").containsExactly(FigureKind.IF_HE_PLAYS,
                        FigureKind.BEFORE_KICKOFF);
        assertThat(figureOf(error, FigureKind.IF_HE_PLAYS).value()).isEqualTo(5.5);
        assertThat(figureOf(error, FigureKind.BEFORE_KICKOFF).value()).isEqualTo(5.9);
    }

    // ------------------------------------------------------------------ the wire

    @Test
    void theSourceSerializesItsSummaryAndItsMeasurementsAndOmitsWhatIsNotKnown() {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();

        JsonNode shipped = json.valueToTree(WeightedAverageProjectionProvider.SOURCE);
        assertThat(names(shipped)).containsExactly("id", "label", "summary", "typicalError");
        JsonNode typical = shipped.get("typicalError");
        assertThat(names(typical)).containsExactly("metric", "figures");
        assertThat(typical.get("figures")).hasSize(2);
        assertThat(typical.get("figures").get(0).get("kind").asText()).isEqualTo("IF_HE_PLAYS");
        assertThat(typical.get("figures").get(1).get("kind").asText()).isEqualTo("BEFORE_KICKOFF");
        for (JsonNode figure : typical.get("figures")) {
            assertThat(names(figure)).containsExactly("kind", "value", "population", "measured");
            assertThat(figure.get("measured")).as("the three measurements each").hasSize(3);
            assertThat(names(figure.get("measured").get(0))).as("a full season: no weeks")
                    .containsExactly("season", "mae", "n");
            assertThat(names(figure.get("measured").get(1))).containsExactly("season", "mae", "n");
            assertThat(names(figure.get("measured").get(2))).containsExactly("season", "weeks", "mae", "n");
        }
        // What a report does not state is left out of the JSON, not sent as null.
        JsonNode unstated = json.valueToTree(new Measurement(2025, null, 5.5, null));
        assertThat(names(unstated)).containsExactly("season", "mae");

        JsonNode bare = json.valueToTree(new ProjectionSource("bare-v1", "Bare", "No published error."));
        assertThat(bare.get("typicalError").isNull()).as("a source with no figure says so").isTrue();
    }

    private static ErrorFigure figureOf(TypicalError error, FigureKind kind) {
        return error.figures().stream().filter(figure -> figure.kind() == kind).findFirst().orElseThrow();
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
