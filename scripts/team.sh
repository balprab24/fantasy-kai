#!/usr/bin/env bash
# Start Claude Code with agent teams on, in a tmux session, one pane per teammate.
#
#     ./scripts/team.sh            # session "fk"
#     ./scripts/team.sh slice5     # named session
#
# Teams are OFF everywhere else on purpose: with them on, any subagent Claude
# names becomes a full teammate, and every teammate is its own context window.
set -euo pipefail
repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
name="${1:-fk}"
command -v tmux >/dev/null || { echo "tmux missing: brew install tmux" >&2; exit 1; }
command -v claude >/dev/null || { echo "claude CLI not on PATH" >&2; exit 1; }
if tmux has-session -t "$name" 2>/dev/null; then
  exec tmux attach -t "$name"
fi
exec tmux new-session -s "$name" -c "$repo" \
  "CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1 claude --teammate-mode tmux"
