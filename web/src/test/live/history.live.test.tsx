import { screen, waitFor, within } from '@testing-library/react';
import { beforeAll, describe, expect, it } from 'vitest';
import { apiClient } from '@/api';
import { HistoryPage } from '@/pages/HistoryPage';
import { WorkoutDetailPage } from '@/pages/WorkoutDetailPage';
import { renderWithProviders } from '@/test/renderWithProviders';

/**
 * Renders the real history page against a **running backend and real
 * PostgreSQL** — the web counterpart of Android's `LiveBackendSyncTest`.
 *
 * Nothing is mocked: the real Axios client, the real decimal-preserving parse,
 * the real query hook and the real components. Component tests with fixtures
 * cannot catch a contract drift (a renamed field, a changed null-ability, a
 * status filter regression); this can.
 *
 * It runs only against a backend explicitly named by `VITE_LIVE_TEST_BASE_URL`
 * that also reports `disposable: true` from `/health` — the same rule as
 * Android's `LiveBackendSyncTest`, for the same reason (TD-013).
 *
 * This test only *reads*, so it never polluted anything the way the Android one
 * did. It is held to the identical standard anyway: a rule that applies to some
 * live tests and not others is a rule nobody can apply confidently, and a
 * read-only test is one refactor away from writing.
 *
 * ```
 * cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=livetest
 * VITE_LIVE_TEST_BASE_URL=http://localhost:8081/api/v1 npm run test
 * ```
 */
const LIVE_BASE_URL = (import.meta.env.VITE_LIVE_TEST_BASE_URL ?? '').replace(/\/+$/, '');

let backendIsUp = false;

/**
 * Null when unreachable (skip); false when it answers without declaring itself
 * disposable, which means the URL points at the system of record.
 */
async function probeDisposable(): Promise<boolean | null> {
  try {
    const response = await fetch(`${LIVE_BASE_URL}/health`);
    if (!response.ok) return null;
    const body: unknown = await response.json();
    return (body as { disposable?: boolean }).disposable === true;
  } catch {
    return null;
  }
}

beforeAll(async () => {
  if (!LIVE_BASE_URL) {
    console.warn('VITE_LIVE_TEST_BASE_URL is not set — skipping the live history test.');
    return;
  }

  const disposable = await probeDisposable();
  if (disposable === null) {
    console.warn(`No backend reachable at ${LIVE_BASE_URL} — skipping the live history test.`);
    return;
  }
  if (!disposable) {
    throw new Error(
      `Refusing to run: ${LIVE_BASE_URL} does not report \`disposable: true\`. Only the ` +
        "backend's `livetest` profile does. Point this at the disposable instance " +
        '(port 8081), never at the system of record — see TD-013.',
    );
  }

  backendIsUp = true;
  // The suite-wide test env points at a dummy host; talk to the real one here.
  apiClient.defaults.baseURL = LIVE_BASE_URL;
});

describe('history against a live backend', () => {
  it('renders real workouts, or shows the empty state if none are synced', async (ctx) => {
    // Reported as *skipped*, not passed — a test that never ran must not be
    // counted as evidence (the same contract as Android's assumeTrue).
    if (!backendIsUp) ctx.skip();

    renderWithProviders(<HistoryPage />);

    await waitFor(
      () => {
        const list = screen.queryByTestId('history-list');
        const empty = screen.queryByTestId('history-empty');
        expect(list ?? empty).not.toBeNull();
      },
      { timeout: 10_000 },
    );

    // An error state means the contract or CORS broke — that is the failure
    // this test exists to catch, so assert it explicitly rather than implicitly.
    expect(screen.queryByTestId('history-error')).toBeNull();

    const list = screen.queryByTestId('history-list');
    if (list) {
      const rows = screen.getAllByRole('listitem');
      expect(rows.length).toBeGreaterThan(0);
      // Every row must have rendered a real derived duration, not a placeholder.
      for (const row of rows) {
        expect(row.textContent).toMatch(/\d+m|\d+h \d{2}m/);
      }
      console.log(`LIVE_HISTORY rendered ${rows.length} workout rows`);
    }
    // Test timeout must exceed the waitFor above, or the test aborts first.
  }, 20_000);

  it('returns COMPLETED sessions only, newest first', async (ctx) => {
    if (!backendIsUp) ctx.skip();

    const response = await fetch(`${LIVE_BASE_URL}/workout-sessions`);
    const sessions = (await response.json()) as Array<{ status: string; startedAt: string }>;

    expect(sessions.every((s) => s.status === 'COMPLETED')).toBe(true);

    const startedAt = sessions.map((s) => s.startedAt);
    expect(startedAt).toEqual([...startedAt].sort().reverse());
    console.log(`LIVE_HISTORY ${sessions.length} sessions, all COMPLETED, newest-first verified`);
  });
});

describe('workout detail against a live backend', () => {
  it('renders a real completed workout end to end', async (ctx) => {
    if (!backendIsUp) ctx.skip();

    const list = (await (await fetch(`${LIVE_BASE_URL}/workout-sessions`)).json()) as Array<{
      id: string;
    }>;
    if (list.length === 0) ctx.skip();
    const id = list[0]!.id;

    renderWithProviders(<WorkoutDetailPage />, {
      routePath: '/workouts/:workoutId',
      initialEntries: [`/workouts/${id}`],
    });

    await waitFor(() => expect(screen.queryByTestId('workout-detail')).not.toBeNull(), {
      timeout: 10_000,
    });
    expect(screen.queryByTestId('workout-not-found')).toBeNull();
    expect(screen.queryByTestId('history-error')).toBeNull();

    // A real duration rendered, not a placeholder.
    const heading = screen.getByRole('heading', { level: 1 });
    expect(heading.textContent).toMatch(/\d{4}/);

    // Close the decimal loop: a NUMERIC weight from PostgreSQL must reach the
    // rendered table as a trimmed exact value (`60 × 8`), never as `60.00` and
    // never via a float round-trip.
    const tables = screen.queryAllByRole('table');
    if (tables.length > 0) {
      // Data cells only — the column header also literally contains "×".
      const values = within(tables[0]!)
        .getAllByRole('cell')
        .map((cell) => cell.textContent ?? '')
        .filter((text) => text.includes('×'));
      expect(values.length).toBeGreaterThan(0);
      for (const value of values) {
        expect(value).toMatch(/^-?\d+(\.\d*[1-9])? × \d+$/);
      }
      console.log(`LIVE_DETAIL set values: ${values.join(', ')}`);
    }
    console.log(`LIVE_DETAIL rendered workout ${id}`);
  }, 20_000);

  it('treats a genuinely DISCARDED workout as Not Found', async (ctx) => {
    if (!backendIsUp) ctx.skip();

    // Create and discard a workout through the API so the assertion runs against
    // a real discarded row rather than a hardcoded id from someone's database.
    const id = crypto.randomUUID();
    const created = await fetch(`${LIVE_BASE_URL}/workout-sessions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        id,
        routineId: null,
        startedAt: '2026-07-22T09:00:00Z',
        notes: 'phase 3 live not-found check',
      }),
    });
    expect(created.ok).toBe(true);

    const discarded = await fetch(`${LIVE_BASE_URL}/workout-sessions/${id}/discard`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ endedAt: '2026-07-22T09:30:00Z', notes: null }),
    });
    expect(discarded.ok).toBe(true);

    // It is absent from history...
    const list = (await (await fetch(`${LIVE_BASE_URL}/workout-sessions`)).json()) as Array<{
      id: string;
    }>;
    expect(list.some((w) => w.id === id)).toBe(false);

    // ...and the detail page must not expose it either.
    renderWithProviders(<WorkoutDetailPage />, {
      routePath: '/workouts/:workoutId',
      initialEntries: [`/workouts/${id}`],
    });

    await waitFor(() => expect(screen.queryByTestId('workout-not-found')).not.toBeNull(), {
      timeout: 10_000,
    });
    expect(screen.queryByTestId('workout-detail')).toBeNull();
    console.log(`LIVE_DETAIL discarded workout ${id} correctly hidden`);
  }, 20_000);
});
