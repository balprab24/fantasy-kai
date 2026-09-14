#!/usr/bin/env bash
#
# Builds the jar the daily ingest runs, and records WHAT it was built from.
#
# `./mvnw package` on its own leaves no evidence of its input, so the only
# question anyone could ask afterwards was "is any file newer than the jar" --
# a question about clocks, not about code. This writes a sha256 of
# backend/src/main + pom.xml next to the jar so the question becomes "is the
# source different", which is the one that matters.
#
#     ./scripts/package.sh              # package, skipping tests
#     ./scripts/package.sh --with-tests # package, running the full suite
#
# Any other arguments are passed through to Maven untouched.
#
# Tests are skipped by default because this exists to refresh the ingest jar,
# and `./mvnw -B verify` is the gate for correctness -- see CLAUDE.md's
# definition of done. Skipping them here is not a claim that they passed, and
# nothing downstream reads the sidecar as if it were.

set -euo pipefail

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=lib/jar-state.sh
source "$repo/scripts/lib/jar-state.sh"

with_tests=0
maven_args=()  # expanded below as ${maven_args[@]+...}: bash 3.2 treats an
               # empty array as unbound under `set -u`, and macOS ships 3.2.
for arg in "$@"; do
    case "$arg" in
        --with-tests) with_tests=1 ;;
        *) maven_args+=("$arg") ;;
    esac
done

if [[ -z "${JAVA_HOME:-}" ]]; then
    JAVA_HOME="$(/usr/libexec/java_home -v 25 2>/dev/null || true)"
    export JAVA_HOME
fi

# java_home returns the newest JDK it has and exits 0 when 25 is absent, so the
# version has to be read from what java PRINTS. pom.xml's enforcer rule catches
# this too, but a build that dies 40 seconds in is a worse message than one that
# refuses in a tenth of a second.
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
    java_version="$("$JAVA_HOME/bin/java" -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')"
    if [[ "$java_version" != "25" ]]; then
        echo "JAVA_HOME is Java $java_version, not 25: $JAVA_HOME" >&2
        echo "java_home only scans /Library/Java/JavaVirtualMachines and ~/Library/..." >&2
        echo "-- a JDK anywhere else does not exist as far as it is concerned." >&2
        exit 1
    fi
fi

cd "$repo/backend"
if (( with_tests )); then
    ./mvnw -B package ${maven_args[@]+"${maven_args[@]}"}
else
    ./mvnw -B package -DskipTests ${maven_args[@]+"${maven_args[@]}"}
fi
cd "$repo"

fk_jar_state "$repo" >/dev/null 2>&1 || true
if [[ ! -f "$FK_JAR" ]]; then
    echo "packaging reported success but produced no jar at $FK_JAR" >&2
    exit 1
fi

# Written AFTER the build, so a failed build leaves the previous (now correct,
# because nothing was replaced) sidecar rather than a fresh one vouching for a
# jar that was never produced.
hash="$(fk_source_hash "$repo")"
if [[ -z "$hash" ]]; then
    echo "could not hash backend/src/main -- refusing to write a sidecar that vouches for nothing" >&2
    exit 1
fi
printf '%s  backend/src/main + backend/pom.xml\n' "$hash" > "$FK_JAR.srcsha"

echo
echo "jar:    $FK_JAR"
echo "source: ${hash:0:12}  (recorded in $(basename "$FK_JAR").srcsha)"
if (( with_tests )); then
    echo "tests:  ran"
else
    echo "tests:  SKIPPED -- run 'cd backend && ./mvnw -B verify' before you trust this"
fi
