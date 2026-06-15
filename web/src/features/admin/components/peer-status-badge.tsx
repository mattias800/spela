import { Badge } from "@/components/ui";
import type { FederationPeer } from "@/generated/schemas";

// ROLE component: maps a peer's pairing + reachability state to a Badge.
export function PeerStatusBadge({ peer }: { peer: FederationPeer }) {
  if (peer.status !== "active") {
    return (
      <Badge variant="default" data-testid="peer-status-badge">
        Pending
      </Badge>
    );
  }
  if (peer.reachable) {
    return (
      <Badge variant="success" data-testid="peer-status-badge">
        Reachable
      </Badge>
    );
  }
  if (peer.lastError) {
    return (
      <Badge variant="danger" data-testid="peer-status-badge">
        Error
      </Badge>
    );
  }
  return (
    <Badge variant="default" data-testid="peer-status-badge">
      No contact yet
    </Badge>
  );
}
