# Scripts

## run-tests.sh

Runs the backend test suite in a disposable git worktree.

```bash
./scripts/run-tests.sh                    # whole suite (110 tests)
./scripts/run-tests.sh -Dtest=FooTest     # extra args are passed to Maven
```

**Use this instead of `mvn test`.** The suite fails in the working copy and passes
everywhere else, for reasons that are unknown; the script sidesteps it by running
at HEAD in a throwaway worktree, then cleaning up. See TD-016 in
`docs/TECH_DEBT.md` for what has been ruled out.

Because a worktree checks out committed content, **uncommitted changes are not
tested**. The script warns when it detects any.

Requires JDK 21 (found automatically via `java_home` if needed) and a PostgreSQL
holding `myfitnesslog_test`. Override the connection with `TEST_DB_URL`,
`TEST_DB_USERNAME` and `TEST_DB_PASSWORD`; it defaults to port **5433**.

## run-prod-local.sh

Loads local production environment variables from
`~/.config/myfitnesslog/prod.env` and starts the backend.

```bash
./scripts/run-prod-local.sh
```
