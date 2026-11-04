package com.fantasykai.leagues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class ProjectionUncertaintyTests {

    /** The committed fit, as docs/perf/projection-accuracy.md records it (Half PPR, 2021-2025). */
    @Test
    void theConstantsAreTheDocumentedFit() {
        assertThat(ProjectionUncertainty.sigma("WR", 10)).isCloseTo(3.501 + 3.192, within(1e-9));
        assertThat(ProjectionUncertainty.sigma("QB", 0)).isCloseTo(6.612, within(1e-9));
    }

    /** A7's shape, which the fit's sanity gate required: QB nearly flat, the rest rising. */
    @Test
    void spreadRisesWithTheProjectionExceptAtQuarterback() {
        double qbRise = ProjectionUncertainty.sigma("QB", 20) - ProjectionUncertainty.sigma("QB", 0);
        for (String position : new String[] {"RB", "WR", "TE"}) {
            double rise = ProjectionUncertainty.sigma(position, 20) - ProjectionUncertainty.sigma(position, 0);
            assertThat(rise).as(position).isGreaterThan(qbRise).isPositive();
        }
    }

    @Test
    void aNegativeProjectionIsTreatedAsZeroAndAnUnmodelledPositionIsRefused() {
        assertThat(ProjectionUncertainty.sigma("RB", -2)).isEqualTo(ProjectionUncertainty.sigma("RB", 0));
        assertThat(ProjectionUncertainty.modeled("K")).isFalse();
        assertThatThrownBy(() -> ProjectionUncertainty.sigma("DST", 5)).isInstanceOf(IllegalArgumentException.class);
    }
}
