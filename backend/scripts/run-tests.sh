#!/usr/bin/env bash
#
# Runs the backend test suite in a disposable git worktree.
#
# WHY THIS EXISTS
#
# The suite fails in the working copy and passes everywhere else. Same commit,
# byte-identical files (`diff -r` reports no difference), same JDK, same
# classpath, same database: 110 tests pass in a fresh clone, in a git worktree,
# and in a plain `cp -R` of the working copy to another path, and ~77 fail in the
# working copy itself. Every context load fails the same way, with Spring unable
# to find a mapper bean whose @Component class is present, compiled, and logged by
# the scanner as an identified candidate.
#
# Ruled out by measurement, not assumption: the application code, the test code,
# JetBrains vs Temurin JDK 21, JDK 26, stale target/ (manual rm -rf, not just
# `mvn clean`), .DS_Store files, file permissions and ACLs, duplicate classes on
# the classpath, class-file contents, test ordering, Android Studio and its
# fsnotifier, Claude Code hooks, file-activated Maven profiles, the effective POM,
# and Surefire's forked JVM command line. The last three are byte-identical
# between a passing and a failing run.
#
# The cause is unknown. Rather than leave the backend suite unrunnable, this
# script makes the workaround a single command. It is containment, not a fix; see
# TD-016 in docs/TECH_DEBT.md.
#
# WHAT IT DOES
#
# Creates a throwaway worktree at the current HEAD, runs the suite there, and
# removes it. Because a worktree checks out committed content, uncommitted local
# changes are NOT included: the script says so before running rather than
# silently testing something other than what you are looking at.
#
# REQUIREMENTS
#
#   * JDK 21 (JAVA_HOME is set below if not already on 21)
#   * A PostgreSQL reachable at TEST_DB_URL with the myfitnesslog_test database
#
# USAGE
#
#   ./scripts/run-tests.sh                 # whole suite
#   ./scripts/run-tests.sh -Dtest=Foo      # any extra args go to Maven
#
set -euo pipefail

REPO_ROOT="$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)"
WORKTREE="$(mktemp -d "${TMPDIR:-/tmp}/mfl-backend-tests.XXXXXX")"

# Test database. Overridable, and matching src/test/resources/application.yml.
# Defaults to 5433 because the Homebrew postgresql@17 instance lives there; the
# server on 5432 is an orphaned 16.x whose binaries were removed by an upgrade.
export TEST_DB_URL="${TEST_DB_URL:-jdbc:postgresql://localhost:5433/myfitnesslog_test}"
export TEST_DB_USERNAME="${TEST_DB_USERNAME:-myfitnesslog}"
export TEST_DB_PASSWORD="${TEST_DB_PASSWORD:-myfitnesslog}"

# The project targets Java 21 (pom.xml). Homebrew's Maven pulls in whatever JDK it
# depends on, which is currently 26, so pin it unless the caller already has 21.
if [[ -z "${JAVA_HOME:-}" ]] || ! "${JAVA_HOME}/bin/java" -version 2>&1 | grep -q '"21'; then
    if JAVA_21="$(/usr/libexec/java_home -v 21 2>/dev/null)"; then
        export JAVA_HOME="$JAVA_21"
    else
        echo "warning: no JDK 21 found; the build may fail (the project targets 21)." >&2
    fi
fi

cleanup() {
    git -C "$REPO_ROOT" worktree remove --force "$WORKTREE" >/dev/null 2>&1 || true
    rm -rf "$WORKTREE"
}
trap cleanup EXIT

if ! git -C "$REPO_ROOT" diff --quiet HEAD -- backend/; then
    echo "note: backend/ has uncommitted changes; a worktree tests HEAD, so they" >&2
    echo "      are NOT included in this run. Commit or stash them first." >&2
    echo >&2
fi

echo "Testing $(git -C "$REPO_ROOT" rev-parse --short HEAD) in a disposable worktree..."
git -C "$REPO_ROOT" worktree add --detach --quiet "$WORKTREE" HEAD

# Not `exec`: that would replace this shell and the EXIT trap would never fire,
# leaving a stale worktree behind after every run. Capture the status instead and
# let the trap clean up, then exit with Maven's result so CI and shell `&&` chains
# still see a failure as a failure.
cd "$WORKTREE/backend"
status=0
mvn -B clean test "$@" || status=$?
exit "$status"
