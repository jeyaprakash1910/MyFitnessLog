# MyFitnessLog — Web Client

The read-only web view of workout history (Milestone 10). It renders data that
reaches the backend via Android synchronization; it never writes.

**Status:** Complete (Milestone 10, Phases 1–4). History list and workout detail
work against the live backend, verified responsive at mobile/tablet/desktop with
zero axe WCAG 2.1 A/AA violations.

## Requirements

- Node **>= 20.19** (verified on v24.13.0), npm 11+
- The backend running on `http://localhost:8080` (see `../backend`)

## Setup

```bash
npm install
cp .env.example .env.local   # optional: only to override the default backend URL
npm run dev                  # http://localhost:5173
```

`.env.development` already points at `http://localhost:8080/api/v1`, so `npm run
dev` works with no extra configuration. Put machine-specific overrides in
`.env.local`, which is gitignored and takes precedence.

> The dev server uses `strictPort`, so it fails if 5173 is taken rather than
> silently moving to 5174. That is intentional: the backend's CORS policy allows
> the `:5173` origin specifically, so a port fallback would produce a dev server
> whose every request is rejected at preflight — a confusing failure that looks
> like a backend bug.

## Scripts

| Command | Purpose |
|---|---|
| `npm run dev` | Dev server with HMR |
| `npm run build` | Typecheck (`tsc -b`) then production build |
| `npm run typecheck` | Types only |
| `npm run lint` | ESLint |
| `npm run format` / `format:check` | Prettier |
| `npm test` | Vitest (single run) |

## Structure

```
src/
  api/         HTTP layer: Axios client, endpoints, error model, response types
  app/         App root, route config, layout shell, TanStack Query client
  components/  Presentational components (history card/list, state views)
  config/      Validated environment access
  format/      Display formatting, mirroring Android's HistoryFormatting.kt
  pages/       Route components
  queries/     TanStack Query hooks and query keys
  test/        Test setup, provider harness, live-backend tests
```

## Two things worth knowing before changing this code

**Decimals are strings.** `weight`, `rpe` and `rir` are Postgres `NUMERIC`.
`JSON.parse` would turn `102.50` into `102.5` and lose the scale the Android
client and backend preserve end to end. `src/api/decimal.ts` rewrites the raw
response text so those fields parse as exact strings, wired in at the Axios
`transformResponse` level. They are for display only — never do arithmetic on
them. Adding another decimal field means adding its key in `decimal.ts`.

**The backend defines what "history" means.** `GET /workout-sessions` returns
COMPLETED workouts only, newest first. Do not re-filter or re-sort client-side;
that rule lives in one place on purpose (API_SPECIFICATION §7), and a test
asserts the rendered order equals the order received.

**Formatting mirrors Android byte-for-byte.** `src/format/history.ts` is a
function-for-function mirror of the Android app's `HistoryFormatting.kt`, which
is the reference UX. The expected strings in `history.test.ts` were captured by
running the real Kotlin over the same fixtures — they are not guesses. If you
change formatting, change it on both clients and re-run that comparison.

**A workout is only viewable if it is COMPLETED.** `GET /workout-sessions/{id}`
is *not* status-filtered — it will return a discarded or in-progress session — so
`useWorkoutDetail` maps anything non-COMPLETED to `null` and the page renders Not
Found. Android does the same (`WorkoutDetailUiState.NotFound`). The two Not Found
paths (unknown id, and non-completed workout) render identical output on purpose:
distinguishing them would confirm that a workout history deliberately hides does
exist.

## Live-backend tests

`src/test/live/` renders real pages against a running backend with nothing
mocked. Those tests **skip** when the backend is unreachable, so the suite works
offline:

```
backend down → 129 passed | 4 skipped
backend up   → 133 passed
```

Start the backend (`cd ../backend && mvn spring-boot:run`) to exercise them.

## Related documentation

- `../docs/API_SPECIFICATION.md` — the frozen contract these types mirror
- `../docs/TECH_STACK.md` §6 — the approved web stack
- `../docs/ROADMAP.md` §14 — Milestone 10 scope and phases

## Accessibility & responsive baseline

Verified in Chrome via Playwright at 375 / 768 / 1280 px: no horizontal page
overflow, and **0 axe-core WCAG 2.1 A/AA violations** on both pages. The
guarantees behind that are asserted in `src/components/accessibility.test.tsx`
so they cannot regress silently:

- heading hierarchy never skips a level; one `h1` per page
- every interactive element uses the shared `.focus-ring` class
- sets render as a real `<table>` with column/row headers and a named,
  keyboard-reachable scroll region
- loading announces via `role="status"`, errors via `role="alert"`
- `prefers-reduced-motion` suppresses the skeleton pulse

Not verified: Firefox/Safari, a real screen-reader pass, physical devices.
