package com.fantasykai.projections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * A source id the database cannot hold is refused where the source is defined -- at startup,
 * for a provider's constant -- not by the first night's insert.
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
}
