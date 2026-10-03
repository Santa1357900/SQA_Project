#!/bin/bash
# One-shot Defects4J setup for WSL Ubuntu (run as root: `wsl -d Ubuntu -u root bash setup_wsl_defects4j.sh <linux-user>`).
# Installs JDK 11 (+8), Perl deps, then clones and initialises Defects4J into /home/<user>/defects4j.
set -euo pipefail
TARGET_USER="${1:-${SUDO_USER:-$USER}}"
HOME_DIR="$(getent passwd "$TARGET_USER" | cut -d: -f6)"
D4J_HOME="$HOME_DIR/defects4j"

echo "== [1/4] apt packages =="
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq openjdk-11-jdk git subversion perl curl wget build-essential cpanminus unzip bzip2 \
  python3 python3-pip libdbi-perl libdbd-csv-perl libjson-perl libjson-parse-perl libstring-interpolate-perl \
  libtext-csv-perl libwww-perl > /dev/null
apt-get install -y -qq openjdk-8-jdk > /dev/null 2>&1 || echo "   (openjdk-8 not available on this Ubuntu, continuing with 11)"

JAVA11="$(ls -d /usr/lib/jvm/java-11-openjdk-* | head -1)"
echo "   JAVA_HOME=$JAVA11"

echo "== [2/4] clone Defects4J =="
if [ ! -d "$D4J_HOME/.git" ]; then
  sudo -u "$TARGET_USER" git clone -q https://github.com/rjust/defects4j.git "$D4J_HOME"
else
  echo "   already cloned"
fi

echo "== [3/4] Perl deps + init.sh (downloads build tools, ~5-10 min) =="
cd "$D4J_HOME"
sudo -u "$TARGET_USER" env JAVA_HOME="$JAVA11" PATH="$JAVA11/bin:$PATH" cpanm --quiet --notest --installdeps . || true
sudo -u "$TARGET_USER" env JAVA_HOME="$JAVA11" PATH="$JAVA11/bin:$PATH" ./init.sh

echo "== [3b] Defects4J 3.0.1 fix: Cli build points at a junit jar that does not exist =="
# projects/Cli/Cli.build.xml references lib/junit-4.12.jar, but only junit-4.12-hamcrest-1.3.jar ships.
# Without hamcrest on the classpath every JUnit 4 suite on Cli 1-34 dies with initializationError.
CLI_BUILD="$D4J_HOME/framework/projects/Cli/Cli.build.xml"
if grep -q 'projects/lib/junit-4.12.jar' "$CLI_BUILD" && [ ! -f "$D4J_HOME/framework/projects/lib/junit-4.12.jar" ]; then
  cp "$CLI_BUILD" "$CLI_BUILD.orig"
  sed -i 's#projects/lib/junit-4.12.jar#projects/lib/junit-4.12-hamcrest-1.3.jar#' "$CLI_BUILD"
  echo "   patched $CLI_BUILD"
fi

echo "== [4/4] shell profile =="
PROFILE="$HOME_DIR/.bashrc"
grep -q "defects4j/framework/bin" "$PROFILE" || cat >> "$PROFILE" <<EOF

# Defects4J (added by Claude/Code/setup_wsl_defects4j.sh)
export JAVA_HOME=$JAVA11
export PATH=\$JAVA_HOME/bin:$D4J_HOME/framework/bin:\$PATH
export DEFECTS4J_BIN=$D4J_HOME/framework/bin/defects4j
EOF
chown "$TARGET_USER" "$PROFILE"

echo "== verify =="
sudo -u "$TARGET_USER" env JAVA_HOME="$JAVA11" PATH="$JAVA11/bin:$D4J_HOME/framework/bin:$PATH" bash -c 'java -version 2>&1 | head -1; defects4j info -p Lang | head -5'
echo "DONE"
