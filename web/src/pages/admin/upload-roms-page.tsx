import { useState, useCallback } from "react";
import { PageLayout, SectionList } from "@/components/layout";
import {
  AlertTriangle,
  CheckCheck,
  XCircle,
  Trash2,
  Loader2,
  Upload,
  ScanSearch,
} from "lucide-react";
import { Button, EmptyState } from "@/components/ui";
import { useToast } from "@/components/ui";
import {
  useStagedUploads,
  useUploadRoms,
  useUploadWritable,
  useScrapeAllUploads,
  useAcceptUpload,
  useRejectUpload,
  useAcceptAllUploads,
  useRejectAllUploads,
  useClearStaging,
  useSetUploadConsole,
} from "@/hooks/use-uploads";
import { DropZone } from "@/features/upload/components/drop-zone";
import {
  UploadProgress,
  type UploadFileStatus,
} from "@/features/upload/components/upload-progress";
import { StagedUploadCard } from "@/features/upload/components/staged-upload-card";
import type { StagedUpload } from "@/types/api";

export function UploadRomsPage() {
  const { toast } = useToast();
  const { data: writable } = useUploadWritable();
  const isReadonly = writable?.writable === false;
  const { data: uploads, isLoading: isLoadingUploads } = useStagedUploads();
  const uploadRoms = useUploadRoms();
  const scrapeAll = useScrapeAllUploads();
  const acceptUpload = useAcceptUpload();
  const rejectUpload = useRejectUpload();
  const acceptAll = useAcceptAllUploads();
  const rejectAll = useRejectAllUploads();
  const clearStaging = useClearStaging();
  const setConsole = useSetUploadConsole();

  const [fileStatuses, setFileStatuses] = useState<UploadFileStatus[]>([]);
  const [acceptingId, setAcceptingId] = useState<string | null>(null);
  const [rejectingId, setRejectingId] = useState<string | null>(null);

  const handleFiles = useCallback(
    async (files: File[]) => {
      const statuses: UploadFileStatus[] = files.map((file) => ({
        file,
        status: "uploading" as const,
      }));
      setFileStatuses(statuses);

      try {
        const result = await uploadRoms.mutateAsync(files);
        setFileStatuses(
          files.map((file) => ({ file, status: "complete" as const })),
        );
        const hasZip = files.some((f) =>
          f.name.toLowerCase().endsWith(".zip"),
        );
        if (hasZip && (result?.length ?? 0) > 0 && (result ?? []).every((r) => r.status === "rejected")) {
          toast("info", "ZIP archive contained no recognized ROM files");
        } else {
          toast("success", `Uploaded ${files.length} file${files.length === 1 ? "" : "s"}`);
        }
      } catch (err) {
        setFileStatuses(
          files.map((file) => ({
            file,
            status: "failed" as const,
            error: err instanceof Error ? err.message : "Upload failed",
          })),
        );
        toast(
          "error",
          err instanceof Error ? err.message : "Upload failed",
        );
      }
    },
    [uploadRoms, toast],
  );

  const handleScrapeAll = useCallback(() => {
    scrapeAll.mutate(undefined, {
      onSuccess: () => {
        toast("success", "Scraping complete");
      },
      onError: (err) => {
        toast(
          "error",
          err instanceof Error ? err.message : "Scrape failed",
        );
      },
    });
  }, [scrapeAll, toast]);

  const handleAccept = useCallback(
    (id: string) => {
      setAcceptingId(id);
      acceptUpload.mutate(id, {
        onSuccess: () => {
          toast("success", "ROM accepted and added to library");
          setAcceptingId(null);
        },
        onError: (err) => {
          toast(
            "error",
            err instanceof Error ? err.message : "Accept failed",
          );
          setAcceptingId(null);
        },
      });
    },
    [acceptUpload, toast],
  );

  const handleReject = useCallback(
    (id: string) => {
      setRejectingId(id);
      rejectUpload.mutate(id, {
        onSuccess: () => {
          toast("info", "ROM rejected");
          setRejectingId(null);
        },
        onError: (err) => {
          toast(
            "error",
            err instanceof Error ? err.message : "Reject failed",
          );
          setRejectingId(null);
        },
      });
    },
    [rejectUpload, toast],
  );

  const handleSetConsole = useCallback(
    (id: string, consoleId: string) => {
      setConsole.mutate(
        { id, consoleId },
        {
          onError: (err) => {
            toast(
              "error",
              err instanceof Error ? err.message : "Failed to set console",
            );
          },
        },
      );
    },
    [setConsole, toast],
  );

  const handleAcceptAll = useCallback(() => {
    acceptAll.mutate(undefined, {
      onSuccess: (data) => {
        const n = data?.accepted ?? 0;
        toast("success", `Accepted ${n} ROM${n === 1 ? "" : "s"}`);
      },
      onError: (err) => {
        toast(
          "error",
          err instanceof Error ? err.message : "Accept all failed",
        );
      },
    });
  }, [acceptAll, toast]);

  const handleRejectAll = useCallback(() => {
    rejectAll.mutate(undefined, {
      onSuccess: (data) => {
        const n = data?.rejected ?? 0;
        toast("info", `Rejected ${n} ROM${n === 1 ? "" : "s"}`);
      },
      onError: (err) => {
        toast(
          "error",
          err instanceof Error ? err.message : "Reject all failed",
        );
      },
    });
  }, [rejectAll, toast]);

  const handleClearStaging = useCallback(() => {
    clearStaging.mutate(undefined, {
      onSuccess: (data) => {
        const n = data?.cleared ?? 0;
        toast("info", `Cleared ${n} staged upload${n === 1 ? "" : "s"}`);
        setFileStatuses([]);
      },
      onError: (err) => {
        toast(
          "error",
          err instanceof Error ? err.message : "Clear staging failed",
        );
      },
    });
  }, [clearStaging, toast]);

  const stagedUploads = (uploads ?? []) as StagedUpload[];
  const hasPendingScrape = stagedUploads.some(
    (u) => u.status === "pending_scrape",
  );
  const hasReviewable = stagedUploads.some(
    (u) => u.status === "ready" || u.status === "duplicate",
  );

  return (
    <PageLayout title="Upload ROMs" subtitle="Upload ROM files to add games to your library.">
      <SectionList className="max-w-4xl">
      {isReadonly && (
        <div
          role="alert"
          className="flex items-start gap-3 rounded-xl border border-warning-500/30 bg-warning-500/10 p-4"
          data-testid="readonly-warning"
        >
          <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-warning-400" />
          <div className="text-sm text-warning-400">
            <p className="font-medium">Uploads unavailable</p>
            <p className="mt-1 text-warning-500">
              {writable?.reason ||
                "Game library directory is read-only."}{" "}
              Check that the game library directory exists and has write
              permissions.
            </p>
          </div>
        </div>
      )}

      <DropZone
        onFiles={handleFiles}
        isUploading={uploadRoms.isPending || isReadonly}
      />

      <UploadProgress files={fileStatuses} />

      {/* Scrape button */}
      {hasPendingScrape && (
        <div className="flex items-center gap-3">
          <Button
            onClick={handleScrapeAll}
            loading={scrapeAll.isPending}
            icon={<ScanSearch className="h-4 w-4" />}
          >
            Scrape Metadata
          </Button>
          <span className="text-sm text-surface-400">
            {stagedUploads.filter((u) => u.status === "pending_scrape").length}{" "}
            file{stagedUploads.filter((u) => u.status === "pending_scrape").length === 1 ? "" : "s"}{" "}
            ready to scrape
          </span>
        </div>
      )}

      {/* Scraping in progress */}
      {scrapeAll.isPending && (
        <div className="rounded-xl bg-surface-800/50 p-4">
          <div className="flex items-center gap-2 text-sm">
            <Loader2 className="h-4 w-4 animate-spin text-brand-400" />
            <span className="text-surface-200">
              Scraping metadata for uploaded ROMs...
            </span>
          </div>
        </div>
      )}

      {/* Batch actions */}
      {stagedUploads.length > 0 && (
        <div className="flex flex-wrap items-center gap-2">
          {hasReviewable && (
            <>
              <Button
                size="sm"
                variant="primary"
                onClick={handleAcceptAll}
                loading={acceptAll.isPending}
                icon={<CheckCheck className="h-4 w-4" />}
                data-testid="accept-all-button"
              >
                Accept All
              </Button>
              <Button
                size="sm"
                variant="danger"
                onClick={handleRejectAll}
                loading={rejectAll.isPending}
                icon={<XCircle className="h-4 w-4" />}
                data-testid="reject-all-button"
              >
                Reject All
              </Button>
            </>
          )}
          <Button
            size="sm"
            variant="ghost"
            onClick={handleClearStaging}
            loading={clearStaging.isPending}
            icon={<Trash2 className="h-4 w-4" />}
            data-testid="clear-staging-button"
          >
            Clear Staging
          </Button>
          <span className="text-xs text-surface-500">
            {stagedUploads.length} staged upload{stagedUploads.length === 1 ? "" : "s"}
          </span>
        </div>
      )}

      {/* Staged uploads list */}
      {isLoadingUploads && (
        <div className="flex items-center gap-2 text-sm text-surface-400">
          <Loader2 className="h-4 w-4 animate-spin" />
          Loading staged uploads...
        </div>
      )}

      {stagedUploads.length > 0 && (
        <div className="grid gap-4" data-testid="staged-uploads-list">
          {stagedUploads.map((upload) => (
            <StagedUploadCard
              key={upload.id}
              upload={upload}
              onAccept={handleAccept}
              onReject={handleReject}
              onSetConsole={handleSetConsole}
              isAccepting={acceptingId === upload.id}
              isRejecting={rejectingId === upload.id}
              isSettingConsole={setConsole.isPending}
            />
          ))}
        </div>
      )}

      {!isLoadingUploads && stagedUploads.length === 0 && fileStatuses.length === 0 && (
        <EmptyState
          icon={Upload}
          title="No staged uploads"
          description="Upload ROM files using the drop zone above. After uploading, you can review and accept them into your library."
        />
      )}
    </SectionList>
    </PageLayout>
  );
}

export default UploadRomsPage;
