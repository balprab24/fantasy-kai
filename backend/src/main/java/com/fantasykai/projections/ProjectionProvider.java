package com.fantasykai.projections;

import java.util.Optional;

/**
 * Produces a raw projected stat line for one player from his history.
 *
 * <p>The boundary between choosing a projection method and everything downstream. A provider
 * knows nothing about fantasy scoring, leagues, rankings, injuries, storage or screens: it reads
 * a {@link PlayerHistory} -- which already cannot see the week being projected -- and returns
 * raw stats, or nothing. Everything after it scores, ranks and shows that line the same way
 * whichever provider made it, so replacing the weighted average with a learned method is a new
 * implementation and a configuration value, and no change downstream.
 *
 * <p>Implementations are deterministic: the same history gives the same line, bit for bit.
 */
public interface ProjectionProvider {

    /** Which method this is, as stored beside its projections. */
    ProjectionSource source();

    /** The projected line for {@code history.target()}, or empty when there is nothing to go on. */
    Optional<RawProjection> project(PlayerHistory history);
}
