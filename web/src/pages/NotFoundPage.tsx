import { Link } from 'react-router-dom';
import { routes } from '@/app/routes';

/** Fallback for any unmatched URL. */
export function NotFoundPage() {
  return (
    <section>
      <h1 className="text-xl font-semibold">Page not found</h1>
      <p className="mt-2 text-sm text-slate-500">
        That page does not exist.{' '}
        <Link to={routes.history} className="focus-ring rounded text-blue-700 underline">
          Back to history
        </Link>
        .
      </p>
    </section>
  );
}
