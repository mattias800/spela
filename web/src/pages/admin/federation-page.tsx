import { useState } from "react";
import { PageLayout, SectionList, TitledSection } from "@/components/layout";
import { FederationPeersTable } from "@/features/admin/components/federation-peers-table";
import { FederationExchangeTable } from "@/features/admin/components/federation-exchange-table";
import { FederationErrorBlock } from "@/features/admin/components/federation-error-block";
import {
  useFederationPeers,
  useFederationExchanges,
  useTestFederationPeer,
} from "@/hooks/use-federation";

export function AdminFederationPage() {
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
  const [testing, setTesting] = useState<string | null>(null);

  const handleTest = (fingerprint: string) => {
    setTesting(fingerprint);
    testPeer.mutate(fingerprint, { onSettled: () => setTesting(null) });
  };

  return (
    <PageLayout
      title="Federation"
      subtitle="Your friend servers and the data flowing between them. Pulls, catalog refreshes, and downloads are recorded below."
    >
      <SectionList>
        <TitledSection title="Friend servers">
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
            />
          )}
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
    </PageLayout>
  );
}

export default AdminFederationPage;
