import { CheckCircle2, AlertCircle, Loader2 } from "lucide-react";
import { formatFileSize } from "@/lib/format";

export interface UploadFileStatus {
  file: File;
  status: "pending" | "uploading" | "complete" | "failed";
  error?: string;
}

interface UploadProgressProps {
  files: UploadFileStatus[];
}

export function UploadProgress({ files }: UploadProgressProps) {
  if (files.length === 0) return null;

  const completed = files.filter((f) => f.status === "complete").length;
  const failed = files.filter((f) => f.status === "failed").length;
  const total = files.length;

  return (
    <div data-comp="UploadProgress" className="space-y-3" data-testid="upload-progress">
      <div className="flex items-center justify-between text-sm">
        <span className="text-surface-300">
          Uploaded {completed} of {total} files
          {failed > 0 && (
            <span className="text-danger-400 ml-2">({failed} failed)</span>
          )}
        </span>
      </div>

      {/* Progress bar */}
      <div className="h-2 w-full rounded-full bg-surface-700">
        <div
          className="h-2 rounded-full bg-brand-500 transition-all duration-300 ease-out"
          style={{ width: `${((completed + failed) / total) * 100}%` }}
        />
      </div>

      <div className="max-h-48 overflow-y-auto space-y-1">
        {files.map((f, i) => (
          <div
            key={i}
            className="flex items-center gap-2 text-sm px-2 py-1 rounded-lg"
          >
            {f.status === "complete" && (
              <CheckCircle2 className="h-4 w-4 text-success-500 flex-shrink-0" />
            )}
            {f.status === "failed" && (
              <AlertCircle className="h-4 w-4 text-danger-400 flex-shrink-0" />
            )}
            {f.status === "uploading" && (
              <Loader2 className="h-4 w-4 text-brand-400 flex-shrink-0 animate-spin" />
            )}
            {f.status === "pending" && (
              <div className="h-4 w-4 rounded-full border-2 border-surface-600 flex-shrink-0" />
            )}
            <span className="truncate flex-1 text-surface-300">
              {f.file.name}
            </span>
            <span className="text-xs text-surface-500 flex-shrink-0">
              {formatFileSize(f.file.size)}
            </span>
            {f.status === "failed" && f.error && (
              <span className="text-xs text-danger-400 flex-shrink-0">
                {f.error}
              </span>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
