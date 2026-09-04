package com.fantasykai.ingest;

/**
 * A release asset that does not exist yet.
 *
 * <p>Normal at a season boundary: the daily job starts pulling
 * {@code stats_player_week_2026.csv} before nflverse publishes it, and again
 * every off-season. Not a failure -- the run is recorded as SKIPPED and the
 * pipeline moves on.
 */
public class AssetNotPublishedException extends IngestException {

    public AssetNotPublishedException(String message) {
        super(message);
    }
}
