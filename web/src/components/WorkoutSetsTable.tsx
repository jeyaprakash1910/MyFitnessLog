import type { WorkoutSet } from '@/api';
import { formatRir, formatRpe, formatWeightReps, setCategoryLabel } from '@/format/history';

/**
 * The performed sets of one exercise.
 *
 * Rendered as a real `<table>` rather than styled rows: this is genuinely
 * tabular data with column headers, so a table gives screen-reader users row and
 * column context that a stack of `<div>`s cannot. Android uses a `Row` per set
 * because Compose has no table primitive — the web platform does, and using it
 * is the more faithful expression of the same information.
 *
 * Values are formatted here from the raw set, mirroring Android's
 * `WorkoutDetailSetRow` mapping. `weight`, `rpe` and `rir` are `DecimalString`s
 * and are formatted textually — never converted to numbers (see `format/history`).
 */
export function WorkoutSetsTable({ sets, label }: { sets: WorkoutSet[]; label: string }) {
  return (
    // The table scrolls inside its own container rather than pushing the page
    // wide: three columns plus a "Working · RPE 8.5 · RIR 2" cell can exceed a
    // narrow phone viewport, and a horizontally scrolling *page* is far worse
    // than a scrolling table.
    //
    // `tabIndex` makes the scroll region keyboard-reachable, which a scrollable
    // container otherwise is not — a keyboard user could see clipped content
    // with no way to scroll to it. A focusable region needs a role and an
    // accessible name, hence `role`/`aria-label` rather than a bare div.
    <div
      className="focus-ring -mx-1 mt-3 overflow-x-auto px-1"
      tabIndex={0}
      role="region"
      aria-label={`Sets for ${label}`}
    >
      <table className="w-full min-w-[20rem] text-sm">
        <caption className="sr-only">Performed sets for {label}</caption>
        <thead>
          <tr className="text-left text-xs uppercase tracking-wide text-slate-500">
            <th scope="col" className="py-1 font-medium">
              Set
            </th>
            <th scope="col" className="py-1 font-medium">
              Weight × Reps
            </th>
            <th scope="col" className="py-1 font-medium">
              Type
            </th>
          </tr>
        </thead>
        <tbody>
          {sets.map((set) => {
            const rpe = formatRpe(set.rpe);
            const rir = formatRir(set.rir);
            return (
              <tr
                key={set.id}
                className="border-t border-slate-100"
                data-testid={`set-row-${set.id}`}
              >
                <th scope="row" className="py-2 pr-2 font-normal text-slate-500">
                  {set.setNumber}
                </th>
                <td className="py-2 pr-2 tabular-nums text-slate-900">
                  {formatWeightReps(set.weight, set.repetitions)}
                </td>
                <td className="py-2 text-slate-500">
                  {setCategoryLabel(set.setCategory)}
                  {rpe !== null && ` · ${rpe}`}
                  {rir !== null && ` · ${rir}`}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
