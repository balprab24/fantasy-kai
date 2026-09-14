#!/usr/bin/env bash
#
# "Is the packaged jar current?" -- asked by three callers, answered once here.
#
# ingest-once.sh refuses to run a stale jar, package.sh records what it built,
# and session-check.sh reports the answer at the top of a session. Three copies
# of this logic would drift, and the one that drifted would be the one guarding
# the pipeline.
#
# WHY THIS EXISTS AT ALL. The original guard was:
#
#     find "$repo/backend/src" -newer "$jar"
#
# -- which compares modification time. `git checkout` rewrites mtimes without
# changing a byte, so switching branches to look at a PR marks the jar stale.
# That happened on 2026-09-12: two files (SecurityConfig.java, an unrelated
# test) came back from a checkout with fresh mtimes, the guard refused, and the
# daily ingest was down for two mornings before anyone asked. The jar was
# correct the entire time.
#
# Two changes fix it:
#
#   1. Compare CONTENT, not mtime -- a sha256 over backend/src/main + pom.xml,
#      recorded beside the jar when it is built.
#   2. Watch only what actually goes into the jar. backend/src/test does not;
#      an edited test cannot change the artefact, and one of the two files that
#      took the pipeline down was a test.
#
# The mtime rule survives as a fallback for a jar with no recorded hash -- but
# it announces itself as the weaker test rather than passing for the stronger
# one. A guess must never be reported as a measurement.
#
# The record lives in target/, beside the jar, so `mvn clean` removes both
# together. That is deliberate: a provenance record that outlives the artefact
# it describes is worse than none, because it would vouch for a jar that no
# longer exists. The cost is that `./mvnw clean verify` drops you back to the
# mtime fallback until the next ./scripts/package.sh, and session-check.sh
# reports exactly that rather than a bare "ok".

# sha256 of stdin / of a NUL-separated file list, on either shasum or sha256sum.
fk__sha_stdin() {
    if command -v shasum >/dev/null 2>&1; then shasum -a 256; else sha256sum; fi
}

fk__sha_files() {
    if command -v shasum >/dev/null 2>&1; then xargs -0 shasum -a 256; else xargs -0 sha256sum; fi
}

# fk_source_hash <repo> -- a stable digest of everything that ends up in the jar.
#
# Paths are relative to backend/ on purpose: hashing absolute paths would make
# the digest depend on where the checkout lives, so moving the repo would read
# as a source change.
fk_source_hash() {
    local repo="$1"
    (
        cd "$repo/backend" 2>/dev/null || return 1
        find src/main pom.xml -type f -print0 \
            | LC_ALL=C sort -z \
            | fk__sha_files \
            | fk__sha_stdin \
            | cut -d' ' -f1
    )
}

# fk_jar_state <repo> -- sets FK_JAR, FK_SIDECAR, FK_JAR_STATE, FK_JAR_BASIS,
# FK_JAR_REASON. Returns 0 fresh, 1 stale, 2 missing.
fk_jar_state() {
    local repo="$1"
    FK_JAR="$repo/backend/target/backend-0.0.1-SNAPSHOT.jar"
    FK_SIDECAR="$FK_JAR.srcsha"

    if [[ ! -f "$FK_JAR" ]]; then
        FK_JAR_STATE=missing
        FK_JAR_BASIS=none
        FK_JAR_REASON="no jar at $FK_JAR -- run: ./scripts/package.sh"
        return 2
    fi

    if [[ -f "$FK_SIDECAR" ]]; then
        FK_JAR_BASIS=hash
        local recorded current
        recorded="$(cut -d' ' -f1 <"$FK_SIDECAR" 2>/dev/null)"
        current="$(fk_source_hash "$repo")"

        if [[ -z "$current" ]]; then
            FK_JAR_STATE=missing
            FK_JAR_BASIS=none
            FK_JAR_REASON="cannot read backend/src/main -- staleness is unknown, not fresh"
            return 2
        fi
        if [[ "$recorded" == "$current" ]]; then
            FK_JAR_STATE=fresh
            FK_JAR_REASON="source hash matches the jar (${current:0:12})"
            return 0
        fi
        # Two different situations produce one mismatch, and they deserve
        # different sentences. If the jar is NEWER than the record beside it,
        # the source did not move -- something rebuilt the jar without going
        # through package.sh, so nothing recorded its provenance. Saying "your
        # source changed" there would be the same species of lie the mtime
        # guard told: confidently naming the wrong cause.
        FK_JAR_STATE=stale
        if [[ "$FK_JAR" -nt "$FK_SIDECAR" ]]; then
            FK_JAR_REASON="the jar was rebuilt without ./scripts/package.sh, so nothing records what it was built from -- rebuild with ./scripts/package.sh"
        else
            FK_JAR_REASON="backend/src/main or pom.xml changed since the jar was built -- run: ./scripts/package.sh"
        fi
        return 1
    fi

    # No hash recorded: a jar from before package.sh existed, or built by a bare
    # `./mvnw package`. Fall back to mtime and label the fallback.
    FK_JAR_BASIS=mtime
    local newer
    newer="$(find "$repo/backend/src/main" "$repo/backend/pom.xml" -type f -newer "$FK_JAR" -print -quit 2>/dev/null)"
    if [[ -z "$newer" ]]; then
        FK_JAR_STATE=fresh
        FK_JAR_REASON="no source newer than the jar -- but by mtime only; rebuild with ./scripts/package.sh to record a hash"
        return 0
    fi
    FK_JAR_STATE=stale
    FK_JAR_REASON="$(basename "$newer") is newer than the jar BY MTIME, which a git checkout is enough to cause -- rebuild with ./scripts/package.sh to compare content instead"
    return 1
}
