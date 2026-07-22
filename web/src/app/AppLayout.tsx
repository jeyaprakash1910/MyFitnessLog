import { Link, Outlet } from 'react-router-dom';
import { routes } from './routes';

/**
 * The application shell reused by every page: a header with the app title and a
 * constrained content column. Deliberately minimal — visual polish is Phase 4.
 * Pages render into the {@link Outlet}.
 */
export function AppLayout() {
  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-3xl items-center px-4 py-4 sm:px-6">
          <Link
            to={routes.history}
            className="focus-ring rounded text-lg font-semibold tracking-tight"
          >
            MyFitnessLog
          </Link>
        </div>
      </header>
      <main className="mx-auto max-w-3xl px-4 py-6 sm:px-6 sm:py-8">
        <Outlet />
      </main>
    </div>
  );
}
