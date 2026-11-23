#!/usr/bin/env bash
# TaskCompleted hook: a task cannot be marked complete while session-check
# reports FAIL or drift. Exit 2 blocks completion and feeds the output back
# to the agent that tried to close it. ~3s, read-only, no network.
cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0
cat >/dev/null   # drain the hook's JSON input
out="$(./scripts/session-check.sh --no-network 2>&1)"
code=$?
if [ "$code" -ne 0 ]; then
  {
    echo "session-check reports FAIL or drift -- fix it before closing this task:"
    echo "$out" | grep -E 'FAIL|drift'
  } >&2
  exit 2
fi
exit 0
