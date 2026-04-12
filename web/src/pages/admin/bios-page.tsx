import { useState, useCallback, useRef } from "react";
import { PageLayout, SectionList } from "@/components/layout";
import { Upload, Cpu, Download } from "lucide-react";
import {
  Button,
  Skeleton,
  EmptyState,
  ConfirmDeleteModal,
} from "@/components/ui";
import { useToast } from "@/components/ui";
import {
  useBiosStatus,
  useUploadBiosFile,
  useDeleteBiosFile,
  useDownloadBios,
} from "@/hooks/use-bios";
import { BiosConsoleCard } from "@/features/bios/components/bios-console-card";
import {
  BiosUploadResults,
  type UploadResult,
} from "@/features/bios/components/bios-upload-results";
import type { BiosConsole } from "@/types/api";

function sortConsoles(consoles: BiosConsole[]): BiosConsole[] {
  const statusOrder: Record<BiosConsole["status"], number> = {
    missing: 0,
    invalid: 1,
    ready: 2,
    not_required: 3,
  };
  return [...consoles].sort(
    (a, b) => statusOrder[a.status] - statusOrder[b.status],
  );
}

export function AdminBiosPage() {
  const { data: biosData, isLoading } = useBiosStatus();
  const uploadBios = useUploadBiosFile();
  const deleteBios = useDeleteBiosFile();
  const downloadBios = useDownloadBios();
  const { toast } = useToast();

  const [uploadResults, setUploadResults] = useState<UploadResult[]>([]);
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null);
  const [isDragOver, setIsDragOver] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleFiles = useCallback(
    async (files: FileList | File[]) => {
      const results: UploadResult[] = [];
      for (const file of Array.from(files)) {
        try {
          const result = await uploadBios.mutateAsync(file);
          results.push({ file, result });
        } catch (err) {
          results.push({
            file,
            error: err instanceof Error ? err.message : "Upload failed",
          });
        }
      }
      setUploadResults(results);
    },
    [uploadBios],
  );

  function handleUploadClick() {
    fileInputRef.current?.click();
  }

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    if (e.target.files && e.target.files.length > 0) {
      handleFiles(e.target.files);
      e.target.value = "";
    }
  }

  function handleDrop(e: React.DragEvent) {
    e.preventDefault();
    setIsDragOver(false);
    if (e.dataTransfer.files.length > 0) {
      handleFiles(e.dataTransfer.files);
    }
  }

  function handleDragOver(e: React.DragEvent) {
    e.preventDefault();
    setIsDragOver(true);
  }

  function handleDragLeave() {
    setIsDragOver(false);
  }

  function handleDeleteConfirm() {
    if (!deleteTarget) return;
    deleteBios.mutate(deleteTarget, {
      onSuccess: () => {
        toast("success", `Deleted ${deleteTarget}`);
        setDeleteTarget(null);
      },
      onError: (err) => {
        toast(
          "error",
          err instanceof Error ? err.message : "Delete failed",
        );
        setDeleteTarget(null);
      },
    });
  }

  if (isLoading) {
    return (
      <PageLayout>
        <SectionList className="max-w-4xl">
          <Skeleton className="h-8 w-48" />
          <Skeleton className="h-4 w-80" />
          <Skeleton className="h-32 w-full rounded-2xl" />
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <Skeleton className="h-24 rounded-2xl" />
            <Skeleton className="h-24 rounded-2xl" />
            <Skeleton className="h-24 rounded-2xl" />
          </div>
        </SectionList>
      </PageLayout>
    );
  }

  const consoles = biosData?.consoles ?? [];
  const sortedConsoles = sortConsoles(consoles);
  const hasFiles = (biosData?.files ?? []).length > 0;

  return (
    <PageLayout>
      <SectionList className="max-w-4xl">
      <div>
        <h1 className="text-3xl font-bold text-surface-100">BIOS Files</h1>
        <p className="mt-1 text-surface-400">
          Manage firmware and BIOS files required by emulation cores.
        </p>
      </div>

      {/* Download missing BIOS */}
      <div className="flex items-center gap-4">
        <Button
          onClick={() =>
            downloadBios.mutate(undefined, {
              onSuccess: () => toast("success", "BIOS download complete"),
              onError: (err) =>
                toast(
                  "error",
                  err instanceof Error ? err.message : "Download failed",
                ),
            })
          }
          loading={downloadBios.isPending}
          icon={<Download className="h-4 w-4" />}
        >
          Download Missing BIOS
        </Button>
        <p className="text-xs text-surface-500">
          PS2 BIOS must be uploaded manually
        </p>
      </div>

      {/* Upload zone */}
      <div
        className={`rounded-2xl border-2 border-dashed p-8 text-center transition-colors ${
          isDragOver
            ? "border-brand-500 bg-brand-500/5"
            : "border-surface-700 hover:border-surface-600"
        }`}
        onDrop={handleDrop}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        data-testid="bios-upload-zone"
      >
        <Upload className="h-8 w-8 text-surface-500 mx-auto mb-3" />
        <p className="text-sm text-surface-300 mb-3">
          Drag and drop BIOS files here, or click to browse
        </p>
        <Button onClick={handleUploadClick} loading={uploadBios.isPending} icon={<Upload className="h-4 w-4" />}>
          Upload BIOS Files
        </Button>
        <input
          ref={fileInputRef}
          type="file"
          multiple
          className="hidden"
          onChange={handleFileChange}
          data-testid="bios-file-input"
        />
      </div>

      <BiosUploadResults results={uploadResults} />

      {/* Console cards or empty state */}
      {!hasFiles && consoles.length === 0 ? (
        <EmptyState
          icon={Cpu}
          title="No BIOS files uploaded"
          description="Some consoles require BIOS/firmware files to play games. Upload the required files to get started."
          action={
            <Button onClick={handleUploadClick}>
              <Upload className="h-4 w-4" />
              Upload BIOS Files
            </Button>
          }
        />
      ) : (
        <div>
          <h2 className="text-lg font-semibold text-surface-100 mb-4">
            Console Status
          </h2>
          <div
            className="grid grid-cols-1 md:grid-cols-2 gap-4"
            data-testid="bios-console-grid"
          >
            {sortedConsoles.map((c) => (
              <BiosConsoleCard
                key={c.consoleId}
                console={c}
                onDeleteFile={(fileName) => setDeleteTarget(fileName)}
              />
            ))}
          </div>
        </div>
      )}

      <ConfirmDeleteModal
        open={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        title="Delete BIOS File"
        message={`Are you sure you want to delete "${deleteTarget}"? Games that require this file may not work.`}
        onConfirm={handleDeleteConfirm}
        isPending={deleteBios.isPending}
      />
    </SectionList>
    </PageLayout>
  );
}
