// Shared horizontal progress bar used by the admin scan / scrape /
// grouping status cards. Kept in the admin feature folder because
// it's sized + styled for those operation-status cards; promote to
// `@/components/ui` if a non-admin surface ever needs it.
export function ProgressBar({ value, max }: { value: number; max: number }) {
  const pct = max > 0 ? Math.round((value / max) * 100) : 0;
  return (
    <div data-comp="ProgressBar" className="h-2 w-full rounded-full bg-surface-700">
      <div
        className="h-2 rounded-full bg-brand-500 transition-all duration-300 ease-out"
        style={{ width: `${pct}%` }}
      />
    </div>
  );
}
