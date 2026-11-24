package com.fantasykai.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// No @Validated: see RankingsController.

/**
 * Read-only projections: routing and parameter validation only. What a response contains, and
 * why, is {@link ProjectionsService}'s; nothing here writes, and generation never runs inside a
 * request -- the job does that, and is off.
 *
 * <p>The contract is {@code docs/projections-v1-architecture.md} section 9, "as built", and the
 * plan that built it is {@code docs/projections-slice-5-plan.md}.
 */
@RestController
@RequestMapping("/api/v1")
public class ProjectionController {

    private final ProjectionsService projections;

    public ProjectionController(ProjectionsService projections) {
        this.projections = projections;
    }

    @GetMapping("/projections")
    public ProjectionBoard board(
            @RequestParam long profileId,
            @RequestParam(required = false) String position,
            @RequestParam(defaultValue = "") @Size(max = 60) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + PageResponse.MAX_SIZE)
            @Min(1) @Max(PageResponse.MAX_SIZE) int size,
            @AuthenticationPrincipal Long userId) {
        return projections.board(profileId, position, q, page, size, userId);
    }

    @GetMapping("/players/{id}/projection")
    public PlayerProjectionResponse player(
            @PathVariable long id,
            @RequestParam long profileId,
            @AuthenticationPrincipal Long userId) {
        return projections.player(id, profileId, userId);
    }
}
