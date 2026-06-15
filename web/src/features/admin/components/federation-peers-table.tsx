import { Network } from "lucide-react";
import { Button, Section, EmptyState, TableRowSkeleton } from "@/components/ui";
import { formatDate } from "@/lib/format";
import type { FederationPeer } from "@/generated/schemas";
import { PeerStatusBadge } from "./peer-status-badge";

interface FederationPeersTableProps {
  peers: FederationPeer[] | undefined;
  isLoading: boolean;
  testingFingerprint: string | null;
  onTest: (fingerprint: string) => void;
}

const HEADERS = ["Friend", "Status", "Last contact", "Last error", ""];

export function FederationPeersTable({
  peers,
  isLoading,
  testingFingerprint,
  onTest,
}: FederationPeersTableProps) {
  return (
    <Section>
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="border-b border-surface-800">
              {HEADERS.map((h, i) => (
                <th
                  key={h || `col-${i}`}
                  className="text-left px-5 py-3 text-xs font-semibold uppercase tracking-wider text-surface-400"
                >
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              Array.from({ length: 3 }, (_, i) => (
                <TableRowSkeleton key={i} columns={HEADERS.length} />
              ))
            ) : !peers || peers.length === 0 ? (
              <tr>
                <td colSpan={HEADERS.length}>
                  <EmptyState
                    icon={Network}
                    title="No friend servers"
                    description="Pair with a friend server to start sharing across the mesh."
                  />
                </td>
              </tr>
            ) : (
              peers.map((peer) => (
                <tr
                  key={peer.fingerprint}
                  data-testid="federation-peer-row"
                  className="border-b border-surface-800/50 hover:bg-surface-800/20 transition-colors"
                >
                  <td className="px-5 py-3">
                    <div className="text-sm font-medium text-surface-200">
                      {peer.name || "Unnamed friend"}
                    </div>
                    <div className="font-mono text-xs text-surface-500">
                      {peer.fingerprint.slice(0, 16)}…
                    </div>
                  </td>
                  <td className="px-5 py-3">
                    <PeerStatusBadge peer={peer} />
                  </td>
                  <td className="px-5 py-3 text-sm text-surface-400">
                    {peer.lastContactAt ? formatDate(peer.lastContactAt) : "—"}
                  </td>
                  <td className="px-5 py-3 text-sm text-surface-400 max-w-xs truncate">
                    {peer.lastError || "—"}
                  </td>
                  <td className="px-5 py-3 text-right">
                    <Button
                      variant="secondary"
                      size="sm"
                      loading={testingFingerprint === peer.fingerprint}
                      onClick={() => onTest(peer.fingerprint)}
                      data-testid="test-connection-button"
                    >
                      Test connection
                    </Button>
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
