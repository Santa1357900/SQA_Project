#!/bin/bash
# Compile the generator into ~/aco-work/tool (a path without spaces, needed for -javaagent).
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
TOOL="${ACO_TOOL:-$HOME/aco-work/tool}"
JAVA_HOME_11="$(ls -d /usr/lib/jvm/java-11-openjdk-* 2>/dev/null | head -1)"
[ -n "$JAVA_HOME_11" ] && export JAVA_HOME="$JAVA_HOME_11" PATH="$JAVA_HOME_11/bin:$PATH"
[ -f "$HERE/../Configuration/lib/jacococore.jar" ] || bash "$HERE/setup_lib.sh"
rm -rf "$TOOL" && mkdir -p "$TOOL/classes"
cp "$HERE/../Configuration/lib/"*.jar "$TOOL/"
javac -nowarn -Xlint:-deprecation -cp "$TOOL/jacococore.jar" -d "$TOOL/classes" "$HERE"/AcoReplay.java "$HERE"/AcoOracle.java "$HERE"/AcoSearch.java
echo "built $TOOL"
