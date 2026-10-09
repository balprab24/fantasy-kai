package com.fantasykai.projections;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Which projection method runs.
 *
 * @param provider a {@link ProjectionSource#id()}; {@code weighted-average-v1} is the only one
 */
@ConfigurationProperties(prefix = "fantasykai.projections")
public record ProjectionProperties(@DefaultValue("weighted-average-v1") String provider) {}
