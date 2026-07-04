import { Activity } from "lucide-react";
import { Badge, Section, EmptyState, TableRowSkeleton } from "@/components/ui";
import { formatDate } from "@/lib/format";
import type { FederationExchange } from "@/generated/schemas";

interface FederationExchangeTableProps {
  exchanges: FederationExchange[] | undefined;
  isLoading: boolean;
}

const HEADERS = ["Started", "Server", "Direction", "Operation", "Status", "Items"];

function statusVariant(status: string): "success" | "danger" | "warning" | "default" {
  switch (status) {
    case "ok":
      return "success";
    case "error":
      return "danger";
    case "rejected":
      return "warning";
    default:
      return "default";
  }
}

// Raw API values are snake_case / lowercase (e.g. "stats_pull", "outbound").
// Present them as readable, sentence-cased labels.
function humanize(value: string): string {
  if (!value) return "—";
  const spaced = value.replace(/_/g, " ");
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}

export function FederationExchangeTable({
  exchanges,
  isLoading,
}: FederationExchangeTableProps) {
  return (
    <Section>
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="border-b border-surface-800">
              {HEADERS.map((h) => (
                <th
                  key={h}
                  className="text-left px-5 py-3 text-xs font-semibold uppercase tracking-wider text-surface-400"
                >
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              Array.from({ length: 5 }, (_, i) => (
                <TableRowSkeleton key={i} columns={HEADERS.length} />
              ))
            ) : !exchanges || exchanges.length === 0 ? (
              <tr>
                <td colSpan={HEADERS.length}>
                  <EmptyState
                    icon={Activity}
                    title="No federation activity yet"
                    description="Exchanges with connected servers (stats pulls, catalog refreshes, downloads) will appear here."
                  />
                </td>
              </tr>
            ) : (
              exchanges.map((ex) => (
                <tr
                  key={ex.id}
                  data-testid="federation-exchange-row"
                  className="border-b border-surface-800/50 hover:bg-surface-800/20 transition-colors"
                  title={ex.error || undefined}
                >
                  <td className="px-5 py-3 text-sm text-surface-400 whitespace-nowrap">
                    {formatDate(ex.startedAt)}
                  </td>
                  <td className="px-5 py-3 text-sm text-surface-300">
                    {ex.peerName || `${ex.peerFingerprint.slice(0, 12)}…`}
                  </td>
                  <td className="px-5 py-3 text-sm text-surface-400">
                    {humanize(ex.direction)}
                  </td>
                  <td
                    className="px-5 py-3 text-sm text-surface-400"
                    data-testid="exchange-operation"
                  >
                    {humanize(ex.operation)}
                  </td>
                  <td className="px-5 py-3">
                    <Badge variant={statusVariant(ex.status)}>
                      {humanize(ex.status)}
                    </Badge>
                  </td>
                  <td className="px-5 py-3 text-sm text-surface-400">
                    {ex.itemCount ?? 0}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </Section>
  );
}
