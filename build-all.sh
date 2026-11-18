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

echo "[1/4] Base BOM and parent"
"${MVN[@]}" -q -f base/ff-base-bom/pom.xml install
"${MVN[@]}" -q -f base/ff-base-parent/pom.xml install

echo "[2/4] Base libraries"
for lib in tenant web persistence; do
  echo "    - ff-base-$lib"
  "${MVN[@]}" -f "base/ff-base-$lib/pom.xml" install "$@"
done

echo "[3/4] Platform: identity service, logistics app"
"${MVN[@]}" -f platform/ff-identity/identity-service/pom.xml install "$@"
"${MVN[@]}" -f platform/ff-logistics/ff-logistics-app/pom.xml install "$@"

echo "[4/4] Done."
