package com.fantasykai.projections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
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

    // ------------------------------------------------------------------ the daily job's switch

    /** The application as shipped: application.yml loaded, so its placeholder for the flag is read. */
    private final ApplicationContextRunner shipped = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(Properties.class);

    /** Off by default: a row the job writes locks at kickoff and is never rewritten. */
    @Test
    void theJobIsOffUnlessTheEnvironmentSaysOtherwise() {
        shipped.run(context -> assertThat(context.getBean(ProjectionProperties.class).jobEnabled()).isFalse());
    }

    /**
     * The variable the deploy sets is the one application.yml reads: without a way to change it the
     * flag could never be turned on in production (the CORS origins were once exactly that).
     */
    @Test
    void theVariableTheDeployPassesSwitchesItOnAndOff() {
        shipped.withPropertyValues("PROJECTIONS_JOB_ENABLED=true")
                .run(context -> assertThat(context.getBean(ProjectionProperties.class).jobEnabled()).isTrue());
        shipped.withPropertyValues("PROJECTIONS_JOB_ENABLED=false")
                .run(context -> assertThat(context.getBean(ProjectionProperties.class).jobEnabled()).isFalse());
    }

    /**
     * compose.prod.yml starts the backend with an explicit list of variables, so a variable it does
     * not name never reaches the container. It passes this one, defaulting to off, and the example
     * file tells the operator it exists. A missing file fails the test, it is not skipped; tests run
     * from backend/.
     */
    @Test
    void theProductionComposeFilePassesTheVariableAndDefaultsItOff() throws IOException {
        assertThat(Files.readString(Path.of("..", "deploy", "compose.prod.yml")))
                .contains("PROJECTIONS_JOB_ENABLED: ${PROJECTIONS_JOB_ENABLED:-false}");
        // The assignment line itself, not the name: the file's prose names the variable too.
        assertThat(Files.readString(Path.of("..", "deploy", ".env.example")))
                .contains("\n# PROJECTIONS_JOB_ENABLED=false\n");
    }

    @Test
    void aSourceIdIsAVersionedNameNeverAClass() {
        assertThatThrownBy(() -> new ProjectionSource("WeightedAverageProjectionProvider", "x", "y"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProjectionSource("weighted-average", "x", "y"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
