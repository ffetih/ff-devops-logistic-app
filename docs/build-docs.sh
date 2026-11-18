#!/usr/bin/env bash
# Builds the documentation site with the asciidoctor/docker-asciidoctor image (Docker in WSL or CI).
# Usage:  ./docs/build-docs.sh        -> HTML into docs/build/html
#         ./docs/build-docs.sh pdf    -> HTML and PDF (docs/build/pdf)
# Override the image with ASCIIDOCTOR_IMAGE.
#
# Every guide lives under docs/src. The repository root is mounted. Each guide gets its own folder
# one level below build/html, which keeps the links between guides (../identity/identity-guide.html)
# the same everywhere. Any warning (a broken cross reference, a missing include) fails the build.
set -euo pipefail

IMAGE="${ASCIIDOCTOR_IMAGE:-docker.io/asciidoctor/docker-asciidoctor:1.108.1}"
REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUT=docs/build/html

HTML="asciidoctor -r asciidoctor-diagram --failure-level WARN"
# docs/revisions.sh first: each guide's revision history from git, which the guides include.
BUILD_HTML="sh docs/revisions.sh && $HTML -D $OUT docs/src/index.adoc \
 && $HTML -D $OUT/platform docs/src/platform/platform-guide.adoc \
 && $HTML -D $OUT/identity docs/src/identity/identity-guide.adoc"

echo "Building HTML ..."
docker run --rm -v "$REPO_DIR:/documents" -w /documents "$IMAGE" sh -c "$BUILD_HTML"

if [[ "${1:-}" == "pdf" ]]; then
  echo "Building PDF ..."
  docker run --rm -v "$REPO_DIR:/documents" -w /documents "$IMAGE" \
    asciidoctor-pdf -r asciidoctor-diagram --failure-level WARN -D docs/build/pdf \
    docs/src/platform/platform-guide.adoc docs/src/identity/identity-guide.adoc
fi

echo "Done. Open $REPO_DIR/docs/build/html/index.html"
