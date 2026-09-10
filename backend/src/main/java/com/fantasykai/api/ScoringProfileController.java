package com.fantasykai.api;

import com.fantasykai.query.ScoringProfileQueryRepository;
import com.fantasykai.query.ScoringProfileWriteRepository;
import com.fantasykai.scoring.NoSuchProfileException;
import com.fantasykai.scoring.ScoringProfiles;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
 * §7's {@code /scoring-profiles}, both halves.
 *
 * <p>The read is public and returns the four presets to a logged-out caller,
 * plus the caller's own once there is one. The writes are the first mutations
 * in the codebase.
 *
 * <p>Note: no {@code @Validated} on the class. Both stat controllers carry the
 * same warning and it applies here too -- it proxies the class so Bean
 * Validation throws {@code ConstraintViolationException}, which no MVC handler
 * knows about, and a rejected parameter comes back 500 instead of 400. Spring
 * 6.1+ validates constrained parameters itself. {@code @Valid} on a
 * {@code @RequestBody} is a different mechanism and is safe.
 */
@RestController
@RequestMapping("/api/v1/scoring-profiles")
class ScoringProfileController {

    private final ScoringProfileQueryRepository profiles;
    private final ScoringProfileWriteRepository writes;
    private final ScoringProfiles compiled;

    ScoringProfileController(ScoringProfileQueryRepository profiles,
            ScoringProfileWriteRepository writes, ScoringProfiles compiled) {
        this.profiles = profiles;
        this.writes = writes;
        this.compiled = compiled;
    }

    @GetMapping
    List<ScoringProfileSummary> list(@AuthenticationPrincipal Long userId) {
        return profiles.findVisibleTo(userId).stream()
                .map(row -> new ScoringProfileSummary(row.id(), row.name(), row.preset()))
                .toList();
    }

    /**
     * {@code @PreAuthorize} on every mutation, handoff §8. The filter chain
     * already requires authentication for anything not on its public list, so
     * this is a second, independent statement of the same requirement at the
     * method itself -- the one that survives somebody adding this path to the
     * chain's permitAll list by mistake.
     *
     * <p>The owner is the JWT subject and never a field in the body. §8:
     * "never trust a client-supplied userId".
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ScoringProfileSummary> create(@Valid @RequestBody ProfileRequest request,
            @AuthenticationPrincipal Long userId) {
        // Validate before writing: a ruleset that does not compile must never
        // reach the table, or every later read of it is a 422 the author cannot
        // fix through the API.
        compiled.compile(request.rules());

        long id = writes.create(userId, request.name(), request.rules());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ScoringProfileSummary(id, request.name(), false));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    ScoringProfileSummary update(@PathVariable long id, @Valid @RequestBody ProfileRequest request,
            @AuthenticationPrincipal Long userId) {
        compiled.compile(request.rules());

        if (!writes.update(id, userId, request.name(), request.rules())) {
            // Not found, not yours, or a preset. All 404: a 403 would confirm
            // the id exists, which is the fact being protected.
            throw new NoSuchProfileException("no scoring profile with id " + id);
        }
        // Without this the author keeps scoring against their pre-edit ruleset
        // until the process restarts. evict() existed from Phase 2 and had no
        // callers; this is the one it was written for.
        compiled.evict(id);
        return new ScoringProfileSummary(id, request.name(), false);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<Void> delete(@PathVariable long id, @AuthenticationPrincipal Long userId) {
        if (!writes.delete(id, userId)) {
            throw new NoSuchProfileException("no scoring profile with id " + id);
        }
        compiled.evict(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * @param name  bounded to match {@code scoring_profiles.name VARCHAR(64)}, so
     *              an oversized name is a 400 rather than a database error
     * @param rules the raw ruleset JSON, validated by {@code RulesetValidator}
     *              against {@code StatKey} before it is stored
     */
    record ProfileRequest(
            @NotBlank @Size(max = 64) String name,
            @NotBlank @Size(max = 8192) String rules) {}
}
