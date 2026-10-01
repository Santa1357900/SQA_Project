#!/bin/bash
# Keep validating newly generated suites until every generator log says "Done:" and nothing is pending.
# Usage (inside WSL): bash Claude/Code/validate_loop.sh ["<glob of generation logs>"] [extra validate args...]
cd "$(dirname "$0")/../.." || exit 1
# Generator logs to wait for: every Claude/Results/generation_run1_*.log must contain "Done:".
GEN_LOGS="Claude/Results/generation_run1_*.log"
all_done() {
  local any=0
  for f in $GEN_LOGS; do
    [ -f "$f" ] || continue
    any=1
    grep -q "^Done:" "$f" || return 1
  done
  [ "$any" -eq 1 ]
}
while true; do
  python3 Claude/Code/validate_claude.py --all "$@"
  pending=$(python3 Claude/Code/validate_claude.py --all --dry-run | sed -n 's/.*pending \([0-9]*\).*/\1/p')
  if all_done && [ "${pending:-0}" -eq 0 ]; then
    echo "validate_loop: generation finished and nothing pending - exiting"
    break
  fi
  echo "validate_loop: waiting for new suites (pending=$pending) ..."
  sleep 120
done
