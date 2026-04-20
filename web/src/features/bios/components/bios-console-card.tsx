import { useState } from "react";
import {
  CheckCircle,
  AlertTriangle,
  XCircle,
  ChevronDown,
  ChevronRight,
  Trash2,
} from "lucide-react";
import { Section, Badge, Button, Divider } from "@/components/ui";
import type { BiosConsole, BiosConsoleFile } from "@/types/api";

function statusBadge(status: BiosConsole["status"]) {
  switch (status) {
    case "ready":
      return { label: "Ready", variant: "success" as const };
    case "missing":
      return { label: "Missing BIOS", variant: "danger" as const };
    case "invalid":
      return { label: "Invalid BIOS", variant: "warning" as const };
    case "not_required":
      return { label: "Not Required", variant: "default" as const };
  }
}

function fileStatusIcon(status: BiosConsoleFile["status"]) {
  switch (status) {
    case "valid":
      return <CheckCircle className="h-4 w-4 text-success-500" />;
    case "present":
      return <CheckCircle className="h-4 w-4 text-success-500" />;
    case "invalid":
      return <AlertTriangle className="h-4 w-4 text-warning-500" />;
    case "missing":
      return <XCircle className="h-4 w-4 text-danger-500" />;
  }
}

interface BiosConsoleCardProps {
  console: BiosConsole;
  deEmphasized?: boolean;
  onDeleteFile?: (fileName: string) => void;
}

export function BiosConsoleCard({
  console: biosConsole,
  deEmphasized,
  onDeleteFile,
}: BiosConsoleCardProps) {
  const [expanded, setExpanded] = useState(false);
  const badge = statusBadge(biosConsole.status);
  const totalPresent =
    biosConsole.requiredPresent + biosConsole.optionalPresent;
  const totalFiles = biosConsole.requiredTotal + biosConsole.optionalTotal;

  return (
    <Section
      className={deEmphasized ? "opacity-60" : undefined}
      data-testid={`bios-console-card-${biosConsole.consoleId}`}
    >
      <button
        onClick={() => setExpanded(!expanded)}
        className="w-full text-left px-5 py-4 flex items-center gap-3 cursor-pointer"
      >
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2.5">
            <h3 className="text-sm font-semibold text-surface-100">
              {biosConsole.consoleName}
            </h3>
            <Badge variant={badge.variant}>{badge.label}</Badge>
          </div>
          <p className="text-xs text-surface-500 mt-1">
            {totalPresent}/{totalFiles} files present
          </p>
        </div>
        {expanded ? (
          <ChevronDown className="h-4 w-4 text-surface-500 flex-shrink-0" />
        ) : (
          <ChevronRight className="h-4 w-4 text-surface-500 flex-shrink-0" />
        )}
      </button>

      {expanded && (
        <>
        <Divider />
        <div className="px-5 pb-4 space-y-2 pt-3">
          {(biosConsole.files ?? []).map((file) => (
            <div
              key={file.fileName}
              className="flex items-center gap-2.5 text-sm"
              data-testid={`bios-file-${file.fileName}`}
            >
              {fileStatusIcon(file.status)}
              <div className="flex-1 min-w-0">
                <div>
                  <span className="text-surface-200 font-mono text-xs">
                    {file.fileName}
                  </span>
                  <span className="text-surface-500 ml-2 text-xs">
                    {file.description}
                  </span>
                  {file.required && (
                    <Badge variant="danger" className="ml-2 text-[10px] px-1.5 py-0">
                      Required
                    </Badge>
                  )}
                </div>
                {file.md5 && file.status === "missing" && (
                  <p className="text-[10px] text-surface-600 font-mono mt-0.5">
                    MD5: {file.md5}
                  </p>
                )}
              </div>
              {file.status !== "missing" && onDeleteFile && (
                <Button
                  variant="ghost"
                  size="sm"
                  className="p-1.5 h-auto"
                  onClick={(e) => {
                    e.stopPropagation();
                    onDeleteFile(file.fileName);
                  }}
                  aria-label={`Delete ${file.fileName}`}
                >
                  <Trash2 className="h-3.5 w-3.5 text-surface-400 hover:text-danger-500" />
                </Button>
              )}
            </div>
          ))}
        </div>
        </>
      )}
    </Section>
  );
}
