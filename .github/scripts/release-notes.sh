#!/bin/sh
# Prints the notes of the GitHub Release for a tag: the tag's section of CHANGELOG.md, a quick start on the release's
# compose bundle, and GitHub's New Contributors section. Fails when CHANGELOG.md has no section for the tag.
# usage: release-notes.sh v0.1.0   (needs gh and GITHUB_REPOSITORY, as set in GitHub Actions)
set -eu

tag="$1"
version="${tag#v}"
bundle="spring-transactional-outbox-compose-$version"

[ -f CHANGELOG.md ] || { echo "CHANGELOG.md not found" >&2; exit 1; }

# From "## [0.1.0] - <date>" up to the next version or the link definitions at the bottom
section=$(awk -v heading="## [$version]" '
    index($0, "## [") == 1 { if (found) exit; if (index($0, heading) == 1) { found = 1; next } }
    /^\[[^]]+\]: / { if (found) exit }
    found { print }
' CHANGELOG.md)
if [ -z "$(printf '%s' "$section" | tr -d '[:space:]')" ]; then
    echo "CHANGELOG.md has no section for $version" >&2
    exit 1
fi

generated=$(gh api "repos/$GITHUB_REPOSITORY/releases/generate-notes" -f tag_name="$tag" --jq .body)
contributors=$(printf '%s\n' "$generated" |
    awk '/^## New Contributors/ { found = 1 } /^\*\*Full Changelog/ { exit } found { print }')

printf '%s\n' "$section" | sed -e '/./,$!d'
cat <<NOTES

## Quick start

Download \`$bundle.tar.gz\` from the assets below: the compose files and the monitoring configuration of this
release, set to run its images from GHCR.

\`\`\`bash
tar -xzf $bundle.tar.gz && cd $bundle
docker compose -f docker/compose.servlet.yml up -d --wait
\`\`\`

The order service answers on http://localhost:8180, the notification service on http://localhost:8181. Add
\`--profile observability\` for Prometheus, Grafana, Tempo and OpenSearch.
NOTES
if [ -n "$contributors" ]; then
    printf '\n%s\n' "$contributors"
fi
