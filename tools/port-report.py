#!/usr/bin/env python3
"""
What it costs to move this fork's features onto a newer upstream.

    python tools/port-report.py                 # what each feature is, and its integration surface
    python tools/port-report.py --onto <ref>    # also: which features still apply to <ref>, and where they break

Every feature lives on its own branch off `main`, where `main` is upstream ported to 1.20.1 and
nothing else. A feature is two things: files it adds, which move to a new upstream for free, and
edits into upstream files, which are the entire recurring cost of a version bump. This prints the
second list, because that is the one that has to be re-applied by hand.

--onto does a real trial cherry-pick onto the given ref in a scratch worktree and throws it away
again, so it answers "what would actually break" rather than guessing. Nothing you have is touched.
"""

import argparse
import os
import re
import shutil
import subprocess
import sys
import tempfile

BASE = 'main'


def git(*args, cwd=None, check=False):
    r = subprocess.run(['git'] + list(args), cwd=cwd, capture_output=True, text=True)
    if check and r.returncode:
        sys.exit('git %s failed:\n%s' % (' '.join(args), r.stderr.strip()))
    return r.stdout.strip()


def feature_branches():
    out = git('for-each-ref', '--format=%(refname:short)', 'refs/heads/feat/')
    return [b for b in out.splitlines() if b]


def surface(branch):
    """Files the branch adds, and the upstream files it edits with how many lines it puts in."""
    added = [f for f in git('diff', '--diff-filter=A', '--name-only', BASE, branch).splitlines() if f]
    edited = {}

    for line in git('diff', '--diff-filter=M', '--numstat', BASE, branch).splitlines():
        parts = line.split('\t')

        if len(parts) == 3 and parts[0].isdigit():
            edited[parts[2]] = int(parts[0])

    return added, edited


def short(path):
    return path.split('/mchorse/')[-1] if '/mchorse/' in path else path


def trial(branch, onto):
    """Cherry-pick the branch's commits onto `onto` in a throwaway worktree."""
    commits = [c for c in git('rev-list', '--reverse', '%s..%s' % (BASE, branch)).splitlines() if c]
    tmp = tempfile.mkdtemp(prefix='portcheck-')
    work = os.path.join(tmp, 'w')
    conflicts = []

    try:
        git('worktree', 'add', '--detach', work, onto, check=True)

        for c in commits:
            r = subprocess.run(['git', 'cherry-pick', '-n', c], cwd=work, capture_output=True, text=True)

            if r.returncode:
                stuck = git('diff', '--name-only', '--diff-filter=U', cwd=work).splitlines()
                conflicts.append((c, [s for s in stuck if s]))
                subprocess.run(['git', 'cherry-pick', '--abort'], cwd=work, capture_output=True)
                subprocess.run(['git', 'reset', '--hard'], cwd=work, capture_output=True)
    finally:
        subprocess.run(['git', 'worktree', 'remove', '--force', work], capture_output=True)
        shutil.rmtree(tmp, ignore_errors=True)

    return commits, conflicts


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--onto', help='upstream ref to test the features against, e.g. upstream/dev')
    args = p.parse_args()

    branches = feature_branches()

    if not branches:
        sys.exit('no feat/* branches - nothing to report on')

    total_added = total_edited = total_lines = 0
    print('%-24s %7s %7s %7s' % ('FEATURE', 'ADDS', 'EDITS', 'LINES'))
    print('-' * 50)

    for b in branches:
        added, edited = surface(b)
        lines = sum(edited.values())
        total_added += len(added)
        total_edited += len(edited)
        total_lines += lines
        print('%-24s %7d %7d %7d' % (b, len(added), len(edited), lines))

    print('-' * 50)
    print('%-24s %7d %7d %7d' % ('total', total_added, total_edited, total_lines))
    print()
    print('ADDS move to a new upstream untouched. EDITS and LINES are the re-port cost:')
    print('those hunks go into files upstream also changes, so they are what breaks.')

    print()
    for b in branches:
        added, edited = surface(b)
        print('== %s' % b)

        if not edited:
            print('   self-contained - nothing to re-apply')
        for f, n in sorted(edited.items(), key=lambda kv: -kv[1]):
            print('   %5d  %s' % (n, short(f)))
        print()

    if not args.onto:
        print('run again with --onto upstream/dev to trial-apply each feature to a newer upstream')
        return

    print('=' * 50)
    print('trial applying each feature onto %s' % args.onto)
    print('=' * 50)

    for b in branches:
        commits, conflicts = trial(b, args.onto)

        if not conflicts:
            print('%-24s applies clean (%d commits)' % (b, len(commits)))
            continue

        print('%-24s %d of %d commits conflict' % (b, len(conflicts), len(commits)))

        for c, files in conflicts:
            subject = git('log', '-1', '--format=%s', c)
            print('    %s  %s' % (c[:8], subject))

            for f in files:
                print('        %s' % short(f))


if __name__ == '__main__':
    main()
