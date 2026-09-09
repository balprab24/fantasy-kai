#!/usr/bin/env bash
#
# Installs the daily ingest as a launchd user agent, and proves it landed.
#
# The committed plist is a template: it carries __REPO__ placeholders because a
# checkout's absolute path is not knowable at commit time. Substituting them by
# hand was the documented instruction and it never happened -- the job was never
# loaded, logs/ stayed empty, and ingest_runs recorded nothing on three of the
# four days before the season opened. A script that does the substitution is the
# difference between an instruction and an installation.
#
# Idempotent: re-run it after moving the checkout, or after changing the plist.
#
# Two honest limitations, both inherited from launchd and neither hidden:
#
#   1. StartCalendarInterval fires on the machine's LOCAL time, not ET. Only the
#      in-app @Scheduled job honours America/New_York.
#   2. A sleeping Mac does not run the job at 06:00; launchd runs it on wake.
#      Expect gaps. The freshness health indicator is what makes them visible
#      rather than silent -- see IngestFreshnessHealthIndicator.

set -euo pipefail

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
label="com.fantasykai.ingest"
template="$repo/scripts/$label.plist"
installed="$HOME/Library/LaunchAgents/$label.plist"

if [[ ! -f "$template" ]]; then
    echo "no plist template at $template" >&2
    exit 1
fi

mkdir -p "$HOME/Library/LaunchAgents" "$repo/logs"

# Every __REPO__ in the template, not the two the old comment claimed.
sed "s|__REPO__|$repo|g" "$template" > "$installed"

if grep -q '__REPO__' "$installed"; then
    echo "substitution failed -- $installed still contains __REPO__" >&2
    exit 1
fi

# Unload first so this is re-runnable. A never-loaded job makes unload fail;
# that is expected, not an error.
launchctl unload "$installed" 2>/dev/null || true
launchctl load "$installed"

echo "installed $installed"
echo "  repo:  $repo"
echo "  log:   $repo/logs/ingest.log"
echo "  runs:  daily at 06:00 local time"
echo

if launchctl list | grep -q "$label"; then
    echo "loaded:"
    launchctl list | grep "$label" | sed 's/^/  /'
else
    echo "NOT loaded -- launchctl list has no $label" >&2
    exit 1
fi

echo
echo "run it now:  launchctl start $label"
echo "remove it:   launchctl unload $installed"
