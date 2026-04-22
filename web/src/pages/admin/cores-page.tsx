import { HardDrive, AlertCircle, RefreshCw } from "lucide-react";
import { PageLayout, SectionList } from "@/components/layout";
import { Section, Skeleton, Button, useToast } from "@/components/ui";
import { useAdminCores, useRefreshCore } from "@/hooks/use-admin";
import { formatFileSize, formatRelativeTime } from "@/lib/format";

export function CoresPage() {
  const { data, isLoading, isError } = useAdminCores();
  const { toast } = useToast();
  const refresh = useRefreshCore();

  const onRefresh = (id: number, coreLabel: string) => {
    refresh.mutate(
      { id },
      {
        onSuccess: (res) => {
          if (res?.changed) {
            toast(
              "success",
              `${coreLabel} updated to ${res.sha256.slice(0, 12)}…`,
            );
          } else {
            toast("success", `${coreLabel} already current`);
          }
        },
        onError: (err) => {
          toast(
            "error",
            err instanceof Error ? err.message : "Refresh failed",
          );
        },
      },
    );
  };

  if (isLoading) {
    return (
      <PageLayout>
        <SectionList className="max-w-5xl">
          <Skeleton className="h-8 w-48" />
          <Skeleton className="h-64 w-full rounded-2xl" />
        </SectionList>
      </PageLayout>
    );
  }

  const cores = data ?? [];
  const neverFetched = cores.filter((c) => !c.fetchedAt).length;

  return (
    <PageLayout
      title="Cores"
      subtitle="Factual metadata for the libretro cores this server hosts. The hash and size are computed from the actual binary on disk, not from seed data — use them to confirm which version a player is downloading."
    >
      <SectionList className="max-w-5xl">
        {neverFetched > 0 && (
          <div
            data-testid="cores-never-fetched-banner"
            className="flex items-start gap-3 rounded-xl border border-surface-700 bg-surface-800/50 px-4 py-3"
          >
            <AlertCircle className="h-5 w-5 text-surface-400 flex-shrink-0 mt-0.5" />
            <p className="text-sm text-surface-300">
              {neverFetched} core{neverFetched !== 1 ? "s" : ""} ha
              {neverFetched !== 1 ? "ve" : "s"} no recorded hash yet. The server
              fingerprints each binary the first time a player downloads it, so
              these rows will populate after the next download.
            </p>
          </div>
        )}

        <Section>
          <div className="px-5 pt-5 pb-2">
            <h2 className="text-lg font-semibold text-surface-100 flex items-center gap-2">
              <HardDrive className="h-5 w-5 text-brand-400" />
              Libretro Cores
            </h2>
          </div>
          <div className="px-5 pb-5">
            {isError ? (
              <p className="text-sm text-danger-500">
                Failed to load cores.
              </p>
            ) : cores.length === 0 ? (
              <p className="text-sm text-surface-400">
                No cores registered.
              </p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm" data-testid="cores-table">
                  <thead>
                    <tr className="border-b border-surface-800 text-left">
                      <th className="py-2 pr-4 font-medium text-surface-400">
                        Core
                      </th>
                      <th className="py-2 pr-4 font-medium text-surface-400">
                        Platforms
                      </th>
                      <th className="py-2 pr-4 font-medium text-surface-400">
                        SHA-256
                      </th>
                      <th className="py-2 pr-4 font-medium text-surface-400">
                        Size
                      </th>
                      <th className="py-2 pr-4 font-medium text-surface-400">
                        Last fetched
                      </th>
                      <th className="py-2 pr-4 font-medium text-surface-400">
                        Source
                      </th>
                      <th className="py-2 font-medium text-surface-400 text-right">
                        Actions
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {cores.map((core) => (
                      <tr
                        key={core.id}
                        data-testid={`cores-row-${core.name}`}
                        className="hover:bg-surface-800/30"
                      >
                        <td className="py-2.5 pr-4 text-surface-100">
                          <div className="font-medium">
                            {core.displayName || core.name}
                          </div>
                          <div className="text-xs text-surface-500 font-mono">
                            {core.name}
                          </div>
                        </td>
                        <td className="py-2.5 pr-4 text-surface-300 text-xs">
                          {core.platforms || (
                            <span className="text-surface-500 italic">
                              all
                            </span>
                          )}
                        </td>
                        <td className="py-2.5 pr-4 font-mono text-xs">
                          {core.sha256 ? (
                            <span
                              title={core.sha256}
                              className="text-surface-300"
                            >
                              {core.sha256.slice(0, 12)}…
                            </span>
                          ) : (
                            <span className="text-surface-500 italic">
                              not fetched
                            </span>
                          )}
                        </td>
                        <td className="py-2.5 pr-4 text-surface-300 text-xs">
                          {core.sizeBytes > 0 ? (
                            formatFileSize(core.sizeBytes)
                          ) : (
                            <span className="text-surface-500 italic">—</span>
                          )}
                        </td>
                        <td className="py-2.5 pr-4 text-surface-300 text-xs">
                          {core.fetchedAt ? (
                            <span title={new Date(core.fetchedAt).toISOString()}>
                              {formatRelativeTime(core.fetchedAt)}
                            </span>
                          ) : (
                            <span className="text-surface-500 italic">
                              never
                            </span>
                          )}
                        </td>
                        <td className="py-2.5 pr-4 text-surface-300 font-mono text-xs">
                          {core.sourceUrl ? (
                            <span
                              title={core.sourceUrl}
                              className="break-all"
                            >
                              {truncateUrl(core.sourceUrl)}
                            </span>
                          ) : (
                            <span className="text-surface-500 italic">
                              buildbot
                            </span>
                          )}
                        </td>
                        <td className="py-2.5 text-right">
                          <Button
                            variant="secondary"
                            size="sm"
                            data-testid={`cores-refresh-${core.name}`}
                            disabled={
                              refresh.isPending &&
                              refresh.variables?.id === core.id
                            }
                            onClick={() =>
                              onRefresh(core.id, core.displayName || core.name)
                            }
                            title="Re-hash the on-disk binary and update the recorded sha256"
                          >
                            <RefreshCw
                              className={`h-3.5 w-3.5 ${
                                refresh.isPending &&
                                refresh.variables?.id === core.id
                                  ? "animate-spin"
                                  : ""
                              }`}
                            />
                            <span className="ml-1.5">Refresh</span>
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </Section>
      </SectionList>
    </PageLayout>
  );
}

function truncateUrl(url: string): string {
  if (url.length <= 48) return url;
  return url.slice(0, 45) + "…";
}

export default CoresPage;
