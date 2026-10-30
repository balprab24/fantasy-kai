package com.fantasykai.projections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** The selection seam: one provider bean, chosen by id, and an unknown id refused at startup. */
class ProjectionConfigTests {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ProjectionProperties.class)
    static class Properties {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Properties.class, ProjectionConfig.class);

    @Test
    void theDefaultIsTheWeightedAverage() {
        runner.run(context -> assertThat(context).getBean(ProjectionProvider.class)
                .isInstanceOf(WeightedAverageProjectionProvider.class));
    }

    @Test
    void theIdChoosesTheProvider() {
        runner.withPropertyValues("fantasykai.projections.provider=weighted-average-v1")
                .run(context -> assertThat(context.getBean(ProjectionProvider.class).source().id())
                        .isEqualTo("weighted-average-v1"));
    }

    @Test
    void anUnknownIdStopsTheApplication() {
        runner.withPropertyValues("fantasykai.projections.provider=model-v1")
                .run(context -> assertThat(context).hasFailed());
        assertThatThrownBy(() -> ProjectionConfig.select("WeightedAverageProjectionProvider"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("known: weighted-average-v1");
    }

    @Test
    void aSourceIdIsAVersionedNameNeverAClass() {
        assertThatThrownBy(() -> new ProjectionSource("WeightedAverageProjectionProvider", "x", "y"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProjectionSource("weighted-average", "x", "y"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
