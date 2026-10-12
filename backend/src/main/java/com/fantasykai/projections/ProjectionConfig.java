package com.fantasykai.projections;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one place a projection method is chosen.
 *
 * <p>Exactly one {@link ProjectionProvider} bean exists, picked by
 * {@code fantasykai.projections.provider}. Only the projection job (Slice 3) will inject it;
 * scoring, the API and the UI read stored raw lines and the source beside them, so adding a
 * learned method later is one more case here and a configuration value. An unknown id stops
 * the application at startup rather than projecting with something unintended.
 *
 * <p>No servlet types: this has to exist in the headless one-shot context the daily job runs in.
 */
@Configuration(proxyBeanMethods = false)
class ProjectionConfig {

    @Bean
    ProjectionProvider projectionProvider(ProjectionProperties properties) {
        return select(properties.provider());
    }

    static ProjectionProvider select(String id) {
        if (WeightedAverageProjectionProvider.SOURCE.id().equals(id)) {
            return new WeightedAverageProjectionProvider();
        }
        throw new IllegalStateException("unknown fantasykai.projections.provider \"" + id
                + "\"; known: " + WeightedAverageProjectionProvider.SOURCE.id());
    }
}
