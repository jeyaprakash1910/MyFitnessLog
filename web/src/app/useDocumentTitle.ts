import { useEffect } from 'react';

const BASE_TITLE = 'MyFitnessLog';

/**
 * Sets the browser tab title for the current page.
 *
 * Every route previously showed the same generic title, which matters most for
 * the case the app actively supports: a deep-linked workout. Bookmarks and
 * browser history entries were indistinguishable from each other.
 *
 * Pass `null` while the title is not yet known (e.g. a workout still loading) to
 * keep the base title rather than flashing a placeholder.
 */
export function useDocumentTitle(title: string | null): void {
  useEffect(() => {
    document.title = title === null ? BASE_TITLE : `${title} · ${BASE_TITLE}`;
    // Restore on unmount so a stale title never outlives its page.
    return () => {
      document.title = BASE_TITLE;
    };
  }, [title]);
}
