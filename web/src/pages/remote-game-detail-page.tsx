import { useEffect } from "react";
import { useParams, useLocation, useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { ServerOff } from "lucide-react";
import { PageLayout, SectionList, TitledSection } from "@/components/layout";
import { Button, EmptyState, Skeleton, useToast } from "@/components/ui";
import { ConsoleBadge } from "@/components/console-badge";
import { useAuth } from "@/hooks/use-auth";
import {
  useRemoteGame,
  useImports,
  useStartImport,
  isImportActive,
} from "@/hooks/use-federation-import";
import { ImportsQueue } from "@/features/federation/components/import-status";
import type { CatalogAvailability } from "@/generated/schemas";

// Detail page for a game that lives only on a connected federation server.
// Reached from the ⌘K "On connected servers" results. The data is sparse — we
// only know the cross-key, title, console, a cover, and how many connected
// servers offer it — so the page's job is to let an import-capable user pull it
// into the local library and watch the job progress.
export function RemoteGameDetailPage() {
  const { key = "" } = useParams<{ key: string }>();
  const location = useLocation();
  const navigate = useNavigate();
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const { user } = useAuth();

  const canImport =
    !!user &&
    (user.role === "admin" || user.role === "owner" || user.canImportGames);

  // Coming from search we already have the row; on a deep link / refresh we
  // fetch it by key. Prefer freshly fetched data, fall back to nav state.
  const navState = (location.state as CatalogAvailability | null) ?? null;
  const remoteQuery = useRemoteGame(key);
  const game = remoteQuery.data ?? navState ?? undefined;
  const isLoading = remoteQuery.isLoading && !navState;

  const importsQuery = useImports(canImport);
  const jobs = importsQuery.data ?? [];
  const currentJob = jobs.find((j) => j.key === key); // newest first

  const startImport = useStartImport();

  // Once this game's import completes, refresh the local library so the new
  // game shows up there immediately.
  useEffect(() => {
    if (currentJob?.status === "completed") {
      queryClient.invalidateQueries({ queryKey: ["games"] });
    }
  }, [currentJob?.status, queryClient]);

  function handleImport() {
    if (!game) return;
    startImport.mutate(
      { key: game.key, title: game.title, console: game.console },
      {
        onSuccess: () => toast("success", "Import started"),
        onError: (e) =>
          toast("error", e instanceof Error ? e.message : "Import failed"),
      },
    );
  }

  if (isLoading) {
    return (
      <PageLayout backButtonVariant="standard">
        <div className="flex flex-col gap-6 sm:flex-row">
          <Skeleton className="h-60 w-44 flex-shrink-0 rounded-lg" />
          <div className="flex flex-col gap-3">
            <Skeleton className="h-9 w-64" />
            <Skeleton className="h-5 w-44" />
            <Skeleton className="h-10 w-36" />
          </div>
        </div>
      </PageLayout>
    );
  }

  if (!game) {
    return (
      <PageLayout backButtonVariant="standard">
        <EmptyState
          icon={ServerOff}
          title="Game not available"
          description="No connected server is currently offering this game."
        />
      </PageLayout>
    );
  }

  const servers = game.originCount;
  const importing = currentJob != null && isImportActive(currentJob);

  return (
    <PageLayout backButtonVariant="standard" backLabel="Back">
      <SectionList>
        <div
          className="flex flex-col gap-6 sm:flex-row"
          data-testid="remote-game-detail"
        >
          {game.cover ? (
            <img
              src={game.cover}
              alt=""
              className="h-60 w-44 flex-shrink-0 rounded-lg object-cover shadow-lg"
            />
          ) : (
            <div className="h-60 w-44 flex-shrink-0 rounded-lg bg-surface-800" />
          )}

          <div className="flex min-w-0 flex-col gap-3">
            <div>
              <h1 className="text-3xl font-bold text-surface-100">
                {game.title}
              </h1>
              <div className="mt-2 flex items-center gap-2">
                <ConsoleBadge code={game.console} />
                <span className="text-sm text-surface-400">
                  Available on {servers} connected{" "}
                  {servers === 1 ? "server" : "servers"}
                </span>
              </div>
            </div>

            {/* Call to action — reflects the latest import for this game.
                Detailed per-job progress lives in the queue below. */}
            {currentJob?.status === "completed" && currentJob.gameId != null ? (
              <Button
                onClick={() => navigate(`/games/${currentJob.gameId}`)}
                data-testid="open-imported-game"
                className="self-start"
              >
                View in library
              </Button>
            ) : importing ? (
              <Button
                disabled
                loading
                data-testid="import-in-progress"
                className="self-start"
              >
                Importing…
              </Button>
            ) : canImport ? (
              <div className="flex flex-col gap-2">
                {currentJob?.status === "failed" && currentJob.errorMessage && (
                  <p
                    className="text-sm text-danger-500"
                    data-testid="remote-game-import-error"
                  >
                    Last import failed: {currentJob.errorMessage}
                  </p>
                )}
                <Button
                  onClick={handleImport}
                  loading={startImport.isPending}
                  data-testid="import-game-button"
                  className="self-start"
                >
                  {currentJob?.status === "failed"
                    ? "Retry import"
                    : "Import to library"}
                </Button>
              </div>
            ) : (
              <p className="text-sm text-surface-400" data-testid="import-denied">
                You don't have permission to import games. Ask an admin for
                access.
              </p>
            )}
          </div>
        </div>

        {canImport && (
          <TitledSection title="Import queue">
            <ImportsQueue jobs={jobs} />
          </TitledSection>
        )}
      </SectionList>
    </PageLayout>
  );
}

export default RemoteGameDetailPage;
