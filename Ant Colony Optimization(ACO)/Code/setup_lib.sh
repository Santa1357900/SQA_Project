#!/bin/bash
# Download the JaCoCo jars used for coverage feedback during the search.
set -e
cd "$(dirname "$0")/../Configuration/lib"
BASE=https://repo1.maven.org/maven2/org/jacoco
[ -f jacocoagent.jar ] || curl -sSfL -o jacocoagent.jar $BASE/org.jacoco.agent/0.8.12/org.jacoco.agent-0.8.12-runtime.jar
[ -f jacococore.jar ]  || curl -sSfL -o jacococore.jar  $BASE/org.jacoco.core/0.8.12/org.jacoco.core-0.8.12.jar
sha256sum *.jar > sha256.txt
ls -l
