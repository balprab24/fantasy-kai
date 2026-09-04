package com.fantasykai.ingest;

/** Any failure during a pull-parse-upsert cycle. Recorded in ingest_runs. */
public class IngestException extends RuntimeException {

    public IngestException(String message) {
        super(message);
    }

    public IngestException(String message, Throwable cause) {
        super(message, cause);
    }
}
