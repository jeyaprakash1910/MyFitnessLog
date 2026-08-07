#!/usr/bin/env bash
#
# Runs the backend test suite in a disposable git worktree.
#
# THIS IS NO LONGER REQUIRED
#
# It existed to work around TD-016: the suite failed in the working copy and
# passed in a fresh clone, a worktree or a `cp -R`. That was resolved on
# 2026-08-07. `mvn test` in backend/ now works directly, and is the shorter loop
# because it also covers uncommitted changes.
#
# The cause was VS Code's Red Hat Java extension. It imports backend/ as an
# Eclipse project whose generated classpath declares
# target/generated-sources/annotations as a source folder with no output of its
# own, so it fell back to the project default, target/classes. With autobuild on,
# the language server recompiled MapStruct's generated mappers into Maven's output
# about a second after every build, and Eclipse's compiler emits class files even
# when references do not resolve. The overwritten ExerciseCategoryMapperImpl lost
# its `implements ExerciseCategoryMapper` clause, so Spring registered the bean but
# could not match it to the interface. The fix is `"java.autobuild.enabled": false`
# in .vscode/settings.json.
#
# An earlier version of this header listed "class-file contents" as ruled out by
# measurement. That was wrong, and it was exactly where the answer was. One `cmp`
# between a passing and a failing build would have found it.
#
# This script is kept for now purely as a fallback, since the failure was
# intermittent enough to mislead several investigations. Delete it once the direct
# `mvn test` path has been trusted for a while. See TD-016 in docs/TECH_DEBT.md.
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
