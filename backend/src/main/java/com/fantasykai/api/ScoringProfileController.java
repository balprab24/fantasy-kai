package com.fantasykai.api;

import com.fantasykai.query.ScoringProfileQueryRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The read half of §7's {@code /scoring-profiles}.
 *
 * <p>Ships in Phase 3 because {@code /rankings?profileId=} is unusable without a
 * way to discover the ids. The write half waits for Phase 5, where it needs the
 * authenticated user to isolate against.
 */
@RestController
@RequestMapping("/api/v1/scoring-profiles")
class ScoringProfileController {

    private final ScoringProfileQueryRepository profiles;

    ScoringProfileController(ScoringProfileQueryRepository profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    List<ScoringProfileSummary> list() {
        return profiles.findPresets().stream()
                .map(row -> new ScoringProfileSummary(row.id(), row.name(), row.preset()))
                .toList();
    }
}
