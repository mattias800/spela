import { Gamepad2, Loader2, CheckCircle, Download } from "lucide-react";
import { PageLayout, SectionList } from "@/components/layout";
import { Button, Section } from "@/components/ui";
import { useCheatStats, useImportCheats } from "@/hooks/use-cheats";

export function AdminCheatsPage() {
  const { data: stats, isLoading: statsLoading } = useCheatStats();
  const importCheats = useImportCheats();
  const importResult = importCheats.data;

  return (
    <PageLayout>
      <SectionList className="max-w-3xl">
      <div>
        <h1 className="text-3xl font-bold text-surface-100">Cheats</h1>
        <p className="mt-1 text-surface-400">
          Import and manage cheat codes from the libretro-database.
        </p>
      </div>

      <div className="grid gap-5 md:grid-cols-2">
        <Section>
          <div className="px-5 pt-5 pb-2">
            <h2 className="text-lg font-semibold text-surface-100 flex items-center gap-2">
              <Gamepad2 className="h-5 w-5 text-brand-400" />
              Cheat Stats
            </h2>
          </div>
          <div className="px-5 pb-5">
            {statsLoading ? (
              <div className="flex items-center gap-2 text-sm">
                <Loader2 className="h-4 w-4 animate-spin text-brand-400" />
                <span className="text-surface-200">Loading stats...</span>
              </div>
            ) : stats ? (
              <div className="grid grid-cols-2 gap-3 text-sm">
                <div>
                  <p className="text-surface-500">Total cheats</p>
                  <p className="text-surface-100 font-semibold">
                    {stats.totalCheats}
                  </p>
                </div>
                <div>
                  <p className="text-surface-500">Games with cheats</p>
                  <p className="text-surface-100 font-semibold">
                    {stats.gamesWithCheats}
                  </p>
                </div>
                <div>
                  <p className="text-surface-500">Total games</p>
                  <p className="text-surface-100 font-semibold">
                    {stats.totalGames}
                  </p>
                </div>
                <div>
                  <p className="text-surface-500">Verified games</p>
                  <p className="text-surface-100 font-semibold">
                    {stats.verifiedGames}
                  </p>
                </div>
              </div>
            ) : null}
          </div>
        </Section>

        <Section>
          <div className="px-5 pt-5 pb-2">
            <h2 className="text-lg font-semibold text-surface-100 flex items-center gap-2">
              <Download className="h-5 w-5 text-brand-400" />
              Import Cheats
            </h2>
          </div>
          <div className="px-5 pb-5 space-y-4">
            <p className="text-sm text-surface-400">
              Import cheat codes from the libretro-database. Only verified games
              on supported consoles will be matched.
            </p>

            {importCheats.isPending && (
              <div className="rounded-xl bg-surface-800/50 p-4">
                <div className="flex items-center gap-2 text-sm">
                  <Loader2 className="h-4 w-4 animate-spin text-brand-400" />
                  <span className="text-surface-200">
                    Importing cheat codes...
                  </span>
                </div>
              </div>
            )}

            {importResult && (
              <div className="rounded-xl bg-surface-800/50 p-4 space-y-3">
                <div className="flex items-center gap-2 text-sm">
                  <CheckCircle className="h-4 w-4 text-success-500" />
                  <span className="text-surface-200">Import complete</span>
                </div>
                <div className="grid grid-cols-2 gap-2 text-sm">
                  <div>
                    <p className="text-surface-500">Cheats imported</p>
                    <p className="text-surface-100 font-semibold">
                      {importResult.cheatsImported}
                    </p>
                  </div>
                  <div>
                    <p className="text-surface-500">Games imported</p>
                    <p className="text-surface-100 font-semibold">
                      {importResult.gamesImported}
                    </p>
                  </div>
                  <div>
                    <p className="text-surface-500">Skipped</p>
                    <p className="text-surface-100 font-semibold">
                      {importResult.skipped}
                    </p>
                  </div>
                  {importResult.failed > 0 && (
                    <div>
                      <p className="text-surface-500">Failed</p>
                      <p className="text-error-400 font-semibold">
                        {importResult.failed}
                      </p>
                    </div>
                  )}
                </div>
              </div>
            )}

            <Button
              onClick={() => importCheats.mutate()}
              loading={importCheats.isPending}
              icon={<Download className="h-4 w-4" />}
              className="w-full"
            >
              Import Cheats
            </Button>
          </div>
        </Section>
      </div>
    </SectionList>
    </PageLayout>
  );
}
