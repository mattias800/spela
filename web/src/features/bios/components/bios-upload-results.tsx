import { CheckCircle, AlertTriangle, HelpCircle } from "lucide-react";
import type { BiosFile } from "@/types/api";

interface UploadResult {
  file: File;
  result?: BiosFile;
  error?: string;
}

function UploadResultItem({ item }: { item: UploadResult }) {
  if (item.error) {
    return (
      <div className="flex items-start gap-2.5 text-sm">
        <AlertTriangle className="h-4 w-4 text-danger-500 mt-0.5 flex-shrink-0" />
        <div>
          <span className="text-surface-200 font-mono text-xs">
            {item.file.name}
          </span>
          <p className="text-danger-400 text-xs">{item.error}</p>
        </div>
      </div>
    );
  }

  if (!item.result) return null;

  if (item.result.status === "valid") {
    return (
      <div className="flex items-start gap-2.5 text-sm">
        <CheckCircle className="h-4 w-4 text-success-500 mt-0.5 flex-shrink-0" />
        <div>
          <span className="text-surface-200 font-mono text-xs">
            {item.result.name}
          </span>
          <p className="text-success-400 text-xs">
            Matched: {item.result.consoleName} &mdash; {item.result.description}
          </p>
        </div>
      </div>
    );
  }

  if (item.result.status === "invalid") {
    return (
      <div className="flex items-start gap-2.5 text-sm">
        <AlertTriangle className="h-4 w-4 text-warning-500 mt-0.5 flex-shrink-0" />
        <div>
          <span className="text-surface-200 font-mono text-xs">
            {item.result.name}
          </span>
          <p className="text-warning-400 text-xs">
            Checksum mismatch: expected {item.result.md5}, got different value
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex items-start gap-2.5 text-sm">
      <HelpCircle className="h-4 w-4 text-surface-400 mt-0.5 flex-shrink-0" />
      <div>
        <span className="text-surface-200 font-mono text-xs">
          {item.result.name}
        </span>
        <p className="text-surface-400 text-xs">
          Unrecognized file &mdash; saved to BIOS directory
        </p>
      </div>
    </div>
  );
}

interface BiosUploadResultsProps {
  results: UploadResult[];
}

export function BiosUploadResults({ results }: BiosUploadResultsProps) {
  if (results.length === 0) return null;

  return (
    <div
      className="rounded-xl bg-surface-800/50 p-4 space-y-3"
      data-testid="bios-upload-results"
    >
      <p className="text-sm font-medium text-surface-200">Upload Results</p>
      {results.map((item, i) => (
        <UploadResultItem key={i} item={item} />
      ))}
    </div>
  );
}

export type { UploadResult };
