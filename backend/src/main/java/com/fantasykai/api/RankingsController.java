package com.fantasykai.api;

import com.fantasykai.ingest.IngestProperties;
import com.fantasykai.query.RankingScope;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Clock;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Note: no @Validated. Spring 6.1+ validates constrained controller
// parameters itself and raises HandlerMethodValidationException, which
// ResponseEntityExceptionHandler renders as a 400 problem+json. Adding
// @Validated proxies the class instead and throws
// ConstraintViolationException, which no MVC handler knows about -- a
// size past its cap comes back as a 500. Verified by trying it.

/**
 * §7's {@code /rankings}, and the endpoint the §9 baseline is measured against.
 *
 * <p>{@code profileId} is required rather than defaulted: a ranking is only
 * meaningful relative to a ruleset, and silently picking one would make the
 * number wrong in a way the caller could not see.
 */
@RestController
@RequestMapping("/api/v1/rankings")
class RankingsController {

    private final RankingsService rankings;
    private final IngestProperties ingest;
    private final Clock clock;

    RankingsController(RankingsService rankings, IngestProperties ingest, Clock clock) {
        this.rankings = rankings;
        this.ingest = ingest;
        this.clock = clock;
    }

    @GetMapping
    PageResponse<RankingRow> rank(
            @RequestParam long profileId,
            @RequestParam(required = false) String position,
            @RequestParam(required = false) Integer season,
            @RequestParam(required = false) String scope,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE)
            @Min(1) @Max(PageResponse.MAX_SIZE) int size) {

        return rankings.rank(profileId, position,
                season == null ? ingest.currentSeason(clock) : season,
                RankingScope.from(scope), page, size);
    }
}
