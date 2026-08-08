# Scripts

## run-tests.sh

Runs the backend test suite in a disposable git worktree.

```bash
./scripts/run-tests.sh                    # whole suite (151 tests)
./scripts/run-tests.sh -Dtest=FooTest     # extra args are passed to Maven
```

**Prefer plain `mvn test`.** This script worked around TD-016, which was resolved
on 2026-08-07: VS Code's Java language server was overwriting Maven's
`target/classes`, and `"java.autobuild.enabled": false` fixed it. The script is
kept only as a short-term fallback and should be deleted once the direct path has
been trusted for a while. See TD-016 in `docs/TECH_DEBT.md`.

It is also the weaker option day to day, because a worktree checks out committed
content, so **uncommitted changes are not tested**. The script warns when it
detects any.

The one thing it still does for you is default the connection to port **5433**.
Plain `mvn test` uses the 5432 default from `src/test/resources/application.yml`,
so on this machine export `TEST_DB_URL`, `TEST_DB_USERNAME` and `TEST_DB_PASSWORD`
first (see the backend section of the root README).

Requires JDK 21 (found automatically via `java_home` if needed) and a PostgreSQL
holding `myfitnesslog_test`. Override the connection with `TEST_DB_URL`,
`TEST_DB_USERNAME` and `TEST_DB_PASSWORD`; it defaults to port **5433**.

## run-prod-local.sh

Loads local production environment variables from
`~/.config/myfitnesslog/prod.env` and starts the backend.

```bash
./scripts/run-prod-local.sh
```
