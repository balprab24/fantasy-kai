package com.fantasykai.ingest;

/**
 * Outcome of one source's ingest.
 *
 * @param source       ingest_runs.source, e.g. 'nflverse.stats_player_week'
 * @param rowsRead     rows present in the source file
 * @param rowsUpserted rows written
 * @param rowsSkipped  rows the source shipped that could not be used
 */
public record IngestResult(String source, int rowsRead, int rowsUpserted, int rowsSkipped) {

    public static IngestResult of(String source, int rowsRead, int rowsUpserted) {
        return new IngestResult(source, rowsRead, rowsUpserted, rowsRead - rowsUpserted);
    }
}
