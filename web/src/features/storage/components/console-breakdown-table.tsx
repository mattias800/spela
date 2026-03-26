import { formatFileSize } from "@/lib/format";
import type { StorageConsoleBreakdown } from "@/types/api";

interface ConsoleBreakdownTableProps {
  consoles: StorageConsoleBreakdown[];
}

export function ConsoleBreakdownTable({
  consoles,
}: ConsoleBreakdownTableProps) {
  const sorted = [...consoles].sort((a, b) => b.bytes - a.bytes);

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left">
        <thead>
          <tr className="border-b border-surface-800/50">
            <th className="pb-3 text-xs font-semibold uppercase tracking-wider text-surface-500">
              Console
            </th>
            <th className="pb-3 text-xs font-semibold uppercase tracking-wider text-surface-500 text-right">
              Saves
            </th>
            <th className="pb-3 text-xs font-semibold uppercase tracking-wider text-surface-500 text-right">
              Size
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-surface-800/30">
          {sorted.map((c) => (
            <tr
              key={c.consoleId}
              className="hover:bg-surface-800/30 transition-colors"
            >
              <td className="py-3 text-sm font-medium text-surface-200">
                {c.consoleName}
              </td>
              <td className="py-3 text-sm text-surface-400 text-right tabular-nums">
                {c.saveCount}
              </td>
              <td className="py-3 text-sm text-surface-400 text-right tabular-nums">
                {formatFileSize(c.bytes)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
