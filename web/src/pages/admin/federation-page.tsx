import { useCallback, useMemo, useState } from "react";
import { Plus } from "lucide-react";
import { useSearchParams } from "react-router-dom";
import { PageLayout, SectionList, TitledSection } from "@/components/layout";
import { Button, ConfirmDeleteModal, useToast } from "@/components/ui";
import { FederationPeersTable } from "@/features/admin/components/federation-peers-table";
import { FederationExchangeTable } from "@/features/admin/components/federation-exchange-table";
import {
  FederationExchangeFilters,
  type FederationExchangeFilterValues,
} from "@/features/admin/components/federation-exchange-filters";
import { FederationErrorBlock } from "@/features/admin/components/federation-error-block";
import { PairFriendDialog } from "@/features/admin/components/pair-friend-dialog";
import { PolicyEditorDialog } from "@/features/admin/components/policy-editor-dialog";
import { FederationRelayToggle } from "@/features/admin/components/federation-relay-toggle";
import {
  useFederationPeers,
  useFederationExchanges,
  type FederationExchangeFilters as FederationExchangeQueryFilters,
  useTestFederationPeer,
  useRevokeFederationPeer,
} from "@/hooks/use-federation";
import type { FederationPeer } from "@/generated/schemas";

const EXCHANGE_LIMIT = 50;

function datetimeLocalToRfc3339(value: string): string | undefined {
  if (!value) return undefined;
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return undefined;
  return parsed.toISOString();
}

export function AdminFederationPage() {
  const { toast } = useToast();
  const [searchParams, setSearchParams] = useSearchParams();
  const {
    data: peersData,
    isLoading: peersLoading,
    isError: peersError,
    error: peersErrorObj,
    refetch: refetchPeers,
  } = useFederationPeers();

  const exchangeFilters: FederationExchangeFilterValues = useMemo(
    () => ({
      peer: searchParams.get("peer") ?? "",
      direction: searchParams.get("direction") ?? "",
      operation: searchParams.get("operation") ?? "",
      status: searchParams.get("status") ?? "",
      startedAfter: searchParams.get("startedAfter") ?? "",
      startedBefore: searchParams.get("startedBefore") ?? "",
    }),
    [searchParams],
  );
  const exchangeQueryFilters: FederationExchangeQueryFilters = useMemo(
    () => ({
      limit: EXCHANGE_LIMIT,
      peer: exchangeFilters.peer || undefined,
      direction: exchangeFilters.direction || undefined,
      operation: exchangeFilters.operation || undefined,
      status: exchangeFilters.status || undefined,
      startedAfter: datetimeLocalToRfc3339(exchangeFilters.startedAfter),
      startedBefore: datetimeLocalToRfc3339(exchangeFilters.startedBefore),
    }),
    [exchangeFilters],
  );
  const {
    data: exchangesData,
    isLoading: exchangesLoading,
    isError: exchangesError,
    error: exchangesErrorObj,
    refetch: refetchExchanges,
  } = useFederationExchanges(exchangeQueryFilters);
  const testPeer = useTestFederationPeer();
  const revokePeer = useRevokeFederationPeer();
  const [testing, setTesting] = useState<string | null>(null);
  const [pairOpen, setPairOpen] = useState(false);
  const [policyTarget, setPolicyTarget] = useState<FederationPeer | null>(null);
  const [revokeTarget, setRevokeTarget] = useState<FederationPeer | null>(null);

  const updateExchangeFilters = useCallback(
    (updates: Partial<FederationExchangeFilterValues>) => {
      const next = new URLSearchParams(searchParams);
      for (const [key, value] of Object.entries(updates)) {
        if (value) {
          next.set(key, value);
        } else {
          next.delete(key);
        }
      }
      setSearchParams(next, { replace: true });
    },
    [searchParams, setSearchParams],
  );

  const clearExchangeFilters = useCallback(() => {
    const next = new URLSearchParams(searchParams);
    for (const key of [
      "peer",
      "direction",
      "operation",
      "status",
      "startedAfter",
      "startedBefore",
    ]) {
      next.delete(key);
    }
    setSearchParams(next, { replace: true });
  }, [searchParams, setSearchParams]);

  const handleTest = (fingerprint: string) => {
    setTesting(fingerprint);
    testPeer.mutate(fingerprint, { onSettled: () => setTesting(null) });
  };

  const handleRevoke = () => {
    if (!revokeTarget) return;
    revokePeer.mutate(revokeTarget.fingerprint, {
      onSuccess: () => {
        toast("success", "Connected server revoked");
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
      subtitle="Your connected servers and the data flowing between them. Pulls, catalog refreshes, and downloads are recorded below."
    >
      <SectionList>
        <TitledSection
          title="Connected servers"
          renderRight={
            <Button
              variant="primary"
              size="sm"
              onClick={() => setPairOpen(true)}
              data-testid="pair-friend-button"
            >
              <Plus className="h-4 w-4" />
              Add connected server
            </Button>
          }
        >
          {peersError ? (
            <FederationErrorBlock
              title="Failed to load connected servers"
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
            <div className="space-y-4">
              <FederationExchangeFilters
                filters={exchangeFilters}
                peers={peersData?.peers}
                onChange={updateExchangeFilters}
                onClear={clearExchangeFilters}
              />
              <FederationExchangeTable
                exchanges={exchangesData?.exchanges}
                isLoading={exchangesLoading}
              />
            </div>
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
        title="Revoke connected server"
        message={`Revoke "${revokeTarget?.name || "this server"}"? It will be removed as a peer and its shared data will stop appearing across the mesh. You can reconnect later with a new invite.`}
        onConfirm={handleRevoke}
        isPending={revokePeer.isPending}
        actionLabel="Revoke"
      />
    </PageLayout>
  );
}

export default AdminFederationPage;
