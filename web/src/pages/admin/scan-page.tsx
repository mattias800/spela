import { ScanSearch, FolderSearch, CheckCircle } from "lucide-react";
import { Button, Card, CardHeader, CardContent } from "@/components/ui";
import { useScanLibrary, useScrapeMetadata } from "@/hooks/use-admin";
import { useToast } from "@/components/ui";

interface ScanResult {
  newGames?: number;
  updatedGames?: number;
  removedGames?: number;
  totalGames?: number;
}

export function AdminScanPage() {
  const scanLibrary = useScanLibrary();
  const scrapeMetadata = useScrapeMetadata();
  const { toast } = useToast();

  const scanResult = scanLibrary.data as ScanResult | undefined;

  return (
    <div className="space-y-6 max-w-3xl">
      <div>
        <h1 className="text-3xl font-bold text-surface-100">Library Scan</h1>
        <p className="mt-1 text-surface-400">
          Scan game directories and update metadata.
        </p>
      </div>

      <div className="grid gap-5 md:grid-cols-2">
        <Card>
          <CardHeader>
            <h2 className="text-lg font-semibold text-surface-100 flex items-center gap-2">
              <FolderSearch className="h-5 w-5 text-brand-400" />
              Scan for Games
            </h2>
          </CardHeader>
          <CardContent className="space-y-4">
            <p className="text-sm text-surface-400">
              Scan configured game directories for new ROMs. Previously detected
              games will not be duplicated.
            </p>

            {scanResult && (
              <div className="rounded-xl bg-surface-800/50 p-4 space-y-3">
                <div className="flex items-center gap-2 text-sm">
                  <CheckCircle className="h-4 w-4 text-success-500" />
                  <span className="text-surface-200">Scan complete</span>
                </div>
                <div className="grid grid-cols-2 gap-2 text-sm">
                  {scanResult.totalGames !== undefined && (
                    <div>
                      <p className="text-surface-500">Total games</p>
                      <p className="text-surface-100 font-semibold">
                        {scanResult.totalGames}
                      </p>
                    </div>
                  )}
                  {scanResult.newGames !== undefined && (
                    <div>
                      <p className="text-surface-500">New games</p>
                      <p className="text-surface-100 font-semibold">
                        {scanResult.newGames}
                      </p>
                    </div>
                  )}
                  {scanResult.updatedGames !== undefined && (
                    <div>
                      <p className="text-surface-500">Updated</p>
                      <p className="text-surface-100 font-semibold">
                        {scanResult.updatedGames}
                      </p>
                    </div>
                  )}
                  {scanResult.removedGames !== undefined && (
                    <div>
                      <p className="text-surface-500">Removed</p>
                      <p className="text-surface-100 font-semibold">
                        {scanResult.removedGames}
                      </p>
                    </div>
                  )}
                </div>
              </div>
            )}

            <Button
              onClick={() =>
                scanLibrary.mutate(undefined, {
                  onError: (err) =>
                    toast(
                      "error",
                      err instanceof Error ? err.message : "Scan failed",
                    ),
                })
              }
              loading={scanLibrary.isPending}
              className="w-full"
            >
              <ScanSearch className="h-4 w-4" />
              Start Scan
            </Button>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <h2 className="text-lg font-semibold text-surface-100 flex items-center gap-2">
              <ScanSearch className="h-5 w-5 text-brand-400" />
              Scrape Metadata
            </h2>
          </CardHeader>
          <CardContent className="space-y-4">
            <p className="text-sm text-surface-400">
              Fetch cover art, descriptions, and other metadata for games that
              are missing information.
            </p>

            <Button
              onClick={() =>
                scrapeMetadata.mutate(undefined, {
                  onSuccess: () =>
                    toast("success", "Metadata scraping started"),
                  onError: (err) =>
                    toast(
                      "error",
                      err instanceof Error ? err.message : "Scrape failed",
                    ),
                })
              }
              loading={scrapeMetadata.isPending}
              variant="secondary"
              className="w-full"
            >
              <ScanSearch className="h-4 w-4" />
              Scrape Metadata
            </Button>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
