#!/usr/bin/env bash
# =============================================================================
#  Builds every project in dependency order after a fresh clone.
#  Needs a JDK 25 on the PATH (or JAVA_HOME); Maven is downloaded by mvnw.
#
#  There is no root pom.xml. Each step installs what the next one compiles
#  against. Projects are added here in the sprint that introduces them
#  (see docs/backlog.md).
#
#  Usage:  ./build-all.sh                full build with tests
#          ./build-all.sh -DskipTests    extra arguments go to every project
#                                        after the BOM and the parent
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")"
MVN=(./mvnw -B)

echo "[1/2] Base BOM and parent"
"${MVN[@]}" -q -f base/ff-base-bom/pom.xml install
"${MVN[@]}" -q -f base/ff-base-parent/pom.xml install

echo "[2/2] Done."
