# Scripts

## Running the backend tests

There is no script. Use Maven directly:

```bash
cd backend
mvn test
```

`run-tests.sh` used to live here, running the suite in a disposable git worktree
to work around TD-016. That was resolved on 2026-08-07 (VS Code's Java language
server was overwriting Maven's `target/classes`), and the script was deleted on
2026-08-10 after the direct path had been trusted for three days. It was always the
weaker option anyway: a worktree checks out committed content, so uncommitted
changes were never tested.

**If your PostgreSQL is not on 5432**, export the connection first or the run fails
with `Connection to localhost:5432 refused`. The script used to default this to
5433 and it is the one thing lost with it:

```bash
export TEST_DB_URL=jdbc:postgresql://localhost:5433/myfitnesslog_test
export TEST_DB_USERNAME=myfitnesslog
export TEST_DB_PASSWORD=myfitnesslog
```

Requires JDK 21 and a PostgreSQL holding `myfitnesslog_test`.

## run-prod-local.sh

Loads local production environment variables from
`~/.config/myfitnesslog/prod.env` and starts the backend.

```bash
./scripts/run-prod-local.sh
```
