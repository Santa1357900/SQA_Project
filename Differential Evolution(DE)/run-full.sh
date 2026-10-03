#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec bash "$script_dir/run.sh" run --results all-defects4j-v3120-full512-w4-r1 \
  --work "work/all-defects4j-v3120-full512-w4-r1" "$@"
