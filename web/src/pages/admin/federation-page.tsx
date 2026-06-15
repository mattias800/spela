import { useState } from "react";
import { Plus } from "lucide-react";
import { PageLayout, SectionList, TitledSection } from "@/components/layout";
import { Button, ConfirmDeleteModal, useToast } from "@/components/ui";
import { FederationPeersTable } from "@/features/admin/components/federation-peers-table";
import { FederationExchangeTable } from "@/features/admin/components/federation-exchange-table";
import { FederationErrorBlock } from "@/features/admin/components/federation-error-block";
import { PairFriendDialog } from "@/features/admin/components/pair-friend-dialog";
import { PolicyEditorDialog } from "@/features/admin/components/policy-editor-dialog";
import { FederationRelayToggle } from "@/features/admin/components/federation-relay-toggle";
import {
  useFederationPeers,
  useFederationExchanges,
  useTestFederationPeer,
  useRevokeFederationPeer,
} from "@/hooks/use-federation";
import type { FederationPeer } from "@/generated/schemas";

export function AdminFederationPage() {
  const { toast } = useToast();
  const {
    data: peersData,
    isLoading: peersLoading,
    isError: peersError,
    error: peersErrorObj,
    refetch: refetchPeers,
  } = useFederationPeers();
  const {
    data: exchangesData,
    isLoading: exchangesLoading,
    isError: exchangesError,
    error: exchangesErrorObj,
    refetch: refetchExchanges,
  } = useFederationExchanges();
  const testPeer = useTestFederationPeer();
  const revokePeer = useRevokeFederationPeer();
  const [testing, setTesting] = useState<string | null>(null);
  const [pairOpen, setPairOpen] = useState(false);
  const [policyTarget, setPolicyTarget] = useState<FederationPeer | null>(null);
  const [revokeTarget, setRevokeTarget] = useState<FederationPeer | null>(null);

  const handleTest = (fingerprint: string) => {
    setTesting(fingerprint);
    testPeer.mutate(fingerprint, { onSettled: () => setTesting(null) });
  };

  const handleRevoke = () => {
    if (!revokeTarget) return;
    revokePeer.mutate(revokeTarget.fingerprint, {
      onSuccess: () => {
        toast("success", "Friend server revoked");
        setRevokeTarget(null);
      },
      onError: (e) => {
        toast("error", e instanceof Error ? e.message : "Revoke failed");
        setRevokeTarget(null);
      },
    });
  };

  return (
    <PageLayout
      title="Federation"
      subtitle="Your friend servers and the data flowing between them. Pulls, catalog refreshes, and downloads are recorded below."
    >
      <SectionList>
        <TitledSection
          title="Friend servers"
          renderRight={
            <Button
              variant="primary"
              size="sm"
              onClick={() => setPairOpen(true)}
              data-testid="pair-friend-button"
            >
              <Plus className="h-4 w-4" />
              Pair friend server
            </Button>
          }
        >
          {peersError ? (
            <FederationErrorBlock
              title="Failed to load friend servers"
              error={peersErrorObj}
              onRetry={() => refetchPeers()}
            />
          ) : (
            <FederationPeersTable
              peers={peersData?.peers}
              isLoading={peersLoading}
              testingFingerprint={testing}
              onTest={handleTest}
              onEditPolicy={setPolicyTarget}
              onRevoke={setRevokeTarget}
            />
          )}
        </TitledSection>

        <TitledSection title="ROM relay">
          <FederationRelayToggle />
        </TitledSection>

        <TitledSection title="Recent activity">
          {exchangesError ? (
            <FederationErrorBlock
              title="Failed to load federation activity"
              error={exchangesErrorObj}
              onRetry={() => refetchExchanges()}
            />
          ) : (
            <FederationExchangeTable
              exchanges={exchangesData?.exchanges}
              isLoading={exchangesLoading}
            />
          )}
        </TitledSection>
      </SectionList>

      <PairFriendDialog open={pairOpen} onClose={() => setPairOpen(false)} />

      {policyTarget && (
        <PolicyEditorDialog
          peer={policyTarget}
          onClose={() => setPolicyTarget(null)}
        />
      )}

      <ConfirmDeleteModal
        open={revokeTarget !== null}
        onClose={() => setRevokeTarget(null)}
        title="Revoke friend server"
        message={`Revoke "${revokeTarget?.name || "this friend"}"? They will be removed as a peer and their shared data will stop appearing across the mesh. You can re-pair later with a new invite.`}
        onConfirm={handleRevoke}
        isPending={revokePeer.isPending}
        actionLabel="Revoke"
      />
    </PageLayout>
  );
}

export default AdminFederationPage;
