package com.fantasykai.api;

import com.fantasykai.ingest.IngestProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Clock;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Note: no @Validated. Spring 6.1+ validates constrained controller
// parameters itself and raises HandlerMethodValidationException, which
// ResponseEntityExceptionHandler renders as a 400 problem+json. Adding
// @Validated proxies the class instead and throws
// ConstraintViolationException, which no MVC handler knows about -- a
// size past its cap comes back as a 500. Verified by trying it.

/** §7's player endpoints. Public: the stats are public data. */
@RestController
@RequestMapping("/api/v1/players")
class PlayerController {

    private final PlayerService players;
    private final IngestProperties ingest;
    private final Clock clock;

    PlayerController(PlayerService players, IngestProperties ingest, Clock clock) {
        this.players = players;
        this.ingest = ingest;
        this.clock = clock;
    }

    @GetMapping
    PageResponse<PlayerSummary> list(
            @RequestParam(required = false) String position,
            @RequestParam(required = false) String team,
            @RequestParam(required = false) Integer season,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "false") boolean desc,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE)
            @Min(1) @Max(PageResponse.MAX_SIZE) int size) {

        return players.list(position, team, season, sort, desc, page, size);
    }

    @GetMapping("/{id}")
    PlayerDetail byId(@PathVariable long id) {
        return players.byId(id);
    }

    /**
     * @param season defaults to the current NFL season, which before the first
     *               stat file of the year is published is legitimately empty
     */
    @GetMapping("/{id}/gamelog")
    GamelogResponse gamelog(
            @PathVariable long id,
            @RequestParam(required = false) Integer season,
            @RequestParam long profileId) {

        return players.gamelog(id, season == null ? ingest.currentSeason(clock) : season, profileId);
    }
}
