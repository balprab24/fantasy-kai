package com.fantasykai.leagues;

import com.fantasykai.leagues.LeagueDtos.DiscoverRequest;
import com.fantasykai.leagues.LeagueDtos.Discovery;
import com.fantasykai.leagues.LeagueDtos.ImportRequest;
import com.fantasykai.leagues.LeagueDtos.LeagueSummaryView;
import com.fantasykai.leagues.LeagueDtos.LeagueView;
import com.fantasykai.leagues.LeagueDtos.MyTeamRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A member's leagues (plan S1). Every request is authenticated by the chain
 * and every mutation says so again with {@code @PreAuthorize}, as
 * {@code ScoringProfileController} does. The member is the JWT subject, never
 * a field in a body. Another member's league is a 404 on every route.
 *
 * <p>No {@code @Validated} on the class: see {@code ScoringProfileController}.
 */
@RestController
@RequestMapping("/api/v1/leagues")
class LeagueController {

    private final LeagueService leagues;
    private final TeamNeeds needs;

    LeagueController(LeagueService leagues, TeamNeeds needs) {
        this.leagues = leagues;
        this.needs = needs;
    }

    /** A Sleeper account's leagues. Stores nothing: picking one is a separate request. */
    @PostMapping("/sleeper/discover")
    @PreAuthorize("isAuthenticated()")
    Discovery discover(@Valid @RequestBody DiscoverRequest request, @AuthenticationPrincipal Long userId) {
        return leagues.discover(userId, request.username(), request.season());
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<LeagueView> importLeague(@Valid @RequestBody ImportRequest request,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(leagues.importLeague(userId,
                request.platform(), request.username(), request.externalLeagueId(), request.season()));
    }

    @GetMapping
    List<LeagueSummaryView> list(@AuthenticationPrincipal Long userId) {
        return leagues.list(userId);
    }

    @GetMapping("/{id}")
    LeagueView get(@PathVariable long id, @AuthenticationPrincipal Long userId) {
        return leagues.view(userId, id);
    }

    /** Plan S2: each starting slot's rest-of-season expectation against the league median. */
    @GetMapping("/{id}/needs")
    TeamNeeds.View needs(@PathVariable long id, @AuthenticationPrincipal Long userId) {
        return needs.view(userId, id);
    }

    @PostMapping("/{id}/sync")
    @PreAuthorize("isAuthenticated()")
    LeagueView sync(@PathVariable long id, @AuthenticationPrincipal Long userId) {
        return leagues.sync(userId, id);
    }

    @PutMapping("/{id}/my-team")
    @PreAuthorize("isAuthenticated()")
    LeagueView setMyTeam(@PathVariable long id, @Valid @RequestBody MyTeamRequest request,
            @AuthenticationPrincipal Long userId) {
        return leagues.setMyTeam(userId, id, request.externalTeamId());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<Void> delete(@PathVariable long id, @AuthenticationPrincipal Long userId) {
        leagues.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
