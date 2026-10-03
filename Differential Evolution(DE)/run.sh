#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$script_dir"
d4j_root="${DEFECTS4J_HOME:-$HOME/defects4j}"
export PATH="$d4j_root/framework/bin:$PATH"
if [[ -d "$HOME/perl5/lib/perl5" ]]; then
  export PERL5LIB="$HOME/perl5/lib/perl5${PERL5LIB:+:$PERL5LIB}"
fi
export TZ=UTC
exec python3 Code/runner.py "$@"
