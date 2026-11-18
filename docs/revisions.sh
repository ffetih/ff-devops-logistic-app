#!/bin/sh
# Writes each guide's revision history from git, for the build to include. Runs inside the
# asciidoctor image (which has git) before asciidoctor, from build-docs.sh.
#
# For every docs/src/**/*-guide.adoc, two files in docs/build/revisions (not in git):
#   <guide>.adoc          :revnumber: and :revdate: of the guide's latest change, read in its header
#   <guide>-history.adoc  the "Revision history" appendix: every change, newest first
# A change is a commit that touched the guide's file, followed across renames (git log --follow).
# Changes to shared includes (glossary, roles, _attributes.adoc) appear in no guide's history.
# Dates are in DOCS_TZ (default Istanbul, UTC+3, as a POSIX zone: the image has no zone database).
set -eu

OUT=docs/build/revisions
TZ="${DOCS_TZ:-<+03>-3}"
export TZ
mkdir -p "$OUT"

git_() { git -c safe.directory='*' "$@"; }

# https://github.com/<owner>/<repo>/commit/ for a GitHub origin (https or ssh), otherwise empty.
origin=$(git_ remote get-url origin 2>/dev/null || true)
commit_url=$(printf '%s' "$origin" | sed -n \
  -e 's#^https://github\.com/\(.*\)\.git$#https://github.com/\1/commit/#p' \
  -e 's#^https://github\.com/\([^.]*\)$#https://github.com/\1/commit/#p' \
  -e 's#^git@github\.com:\(.*\)\.git$#https://github.com/\1/commit/#p' | head -n 1)

for guide in $(find docs/src -name '*-guide.adoc' | sort); do
  name=$(basename "$guide" .adoc)
  log=$(git_ log --follow --no-merges --date=format-local:%Y-%m-%d --format='%h%x09%ad%x09%s' -- "$guide")
  [ -n "$log" ] || continue

  latest=$(printf '%s\n' "$log" | head -n 1)
  printf ':revnumber: %s\n:revdate: %s\n' \
    "$(printf '%s' "$latest" | cut -f1)" "$(printf '%s' "$latest" | cut -f2)" > "$OUT/$name.adoc"

  {
    printf '[appendix]\n[[revision-history]]\n== Revision history\n\n'
    printf 'Every change to this guide, newest first, from the repository history (`git log --follow`).\n'
    printf 'The revision is the commit; the change is its subject.\n\n'
    printf '[cols="2,2,9",options="header"]\n|===\n| Date | Revision | Change\n\n'
    printf '%s\n' "$log" | while IFS="$(printf '\t')" read -r hash date subject; do
      # The subject as written: no formatting or attributes, and | ] cannot end the cell or the macro.
      safe=$(printf '%s' "$subject" | sed -e 's/\\/\\\\/g' -e 's/\]/\\]/g' -e 's/|/\\|/g')
      if [ -n "$commit_url" ]; then revision="link:${commit_url}${hash}[\`$hash\`]"; else revision="\`$hash\`"; fi
      printf '| %s | %s | pass:c[%s]\n' "$date" "$revision" "$safe"
    done
    printf '|===\n'
  } > "$OUT/$name-history.adoc"
done
echo "Revision history written to $OUT"
