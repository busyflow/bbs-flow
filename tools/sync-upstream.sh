#!/usr/bin/env bash
#
# Pull Wemppy4/bbs-fs dev into this fork.
#
# This repo was a file import, so it shared no history with upstream and every refresh used to
# be a re-port. Commit 9e8f3d25 grafted upstream commit 553f093c7 in as a merge base (an -s ours
# merge: no tree change, one extra parent), so a refresh is now an ordinary three-way merge and
# git carries the fork's edits across upstream's renames by itself.
#
#   tools/sync-upstream.sh              show what is new upstream, change nothing
#   tools/sync-upstream.sh --merge      do the merge onto a dated branch
#
# Two things upstream targets that this fork does not, so expect them in every sync:
#
#   - Minecraft 1.20.4 vs this fork's 1.20.1. Roughly one API difference per sync so far; they
#     surface as compile errors, not as merge conflicts. Keep gradle.properties on 1.20.1.
#   - The gizmo. Upstream's FilmTarget carries the fork's CROWD_MOTION and REPLAY_SHIFT kinds,
#     so a sync conflicts on that enum whenever upstream touches it. Keeping both sides is
#     almost always right.
#
set -euo pipefail

cd "$(dirname "$0")/.."

UPSTREAM_BRANCH="${UPSTREAM_BRANCH:-upstream/dev}"

git fetch upstream --prune

BASE="$(git merge-base HEAD "$UPSTREAM_BRANCH" 2>/dev/null || true)"

if [ -z "$BASE" ]; then
    echo "No merge base with $UPSTREAM_BRANCH."
    echo "The graft is missing - are you on a branch that descends from main?"
    exit 1
fi

COUNT="$(git rev-list --count --no-merges "$BASE".."$UPSTREAM_BRANCH")"

echo "Merge base: $(git log -1 --format='%h %cd %s' --date=short "$BASE")"
echo "New upstream commits: $COUNT"
echo

if [ "$COUNT" = "0" ]; then
    echo "Already up to date."
    exit 0
fi

git log --format='  %h %cd  %s' --date=short --no-merges "$BASE".."$UPSTREAM_BRANCH" | head -60
echo
git diff --shortstat "$BASE".."$UPSTREAM_BRANCH"

if [ "${1:-}" != "--merge" ]; then
    echo
    echo "Run with --merge to merge it."
    exit 0
fi

BRANCH="chore/upstream-sync-$(date +%m%d)"

echo
echo "Merging onto $BRANCH ..."
git checkout -b "$BRANCH"

if ! git merge "$UPSTREAM_BRANCH" --no-commit; then
    echo
    echo "Conflicts in:"
    git diff --diff-filter=U --name-only | sed 's/^/  /'
    echo
    echo "Resolve, then: ./gradlew build && git commit"
    exit 1
fi

echo
echo "Merged with no conflicts. Now: ./gradlew build && git commit"
