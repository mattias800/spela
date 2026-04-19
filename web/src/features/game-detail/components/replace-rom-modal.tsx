import { useState, useRef } from "react";
import { CheckCircle2, AlertTriangle, Upload, FileUp } from "lucide-react";
import { Modal, Button, Badge } from "@/components/ui";
import { useReplaceRom } from "@/hooks/use-games";
import { formatFileSize } from "@/lib/format";
import { cn } from "@/lib/cn";
import type { ReplaceROMResult } from "@/types/api";

const ACCEPTED_EXTENSIONS = [
  ".zip",
  ".nes",
  ".sfc",
  ".smc",
  ".gba",
  ".gb",
  ".gbc",
  ".md",
  ".gen",
  ".n64",
  ".z64",
  ".v64",
  ".nds",
  ".iso",
  ".bin",
  ".cue",
  ".img",
  ".chd",
  ".pce",
  ".ngp",
  ".ngc",
  ".ws",
  ".wsc",
  ".a26",
  ".a78",
  ".lnx",
  ".lyx",
  ".jag",
  ".col",
  ".sg",
  ".gg",
  ".sms",
  ".32x",
  ".vec",
];

const ACCEPT_STRING = ACCEPTED_EXTENSIONS.join(",");

interface ReplaceRomModalProps {
  gameId: string;
  open: boolean;
  onClose: () => void;
}

export function ReplaceRomModal({
  gameId,
  open,
  onClose,
}: ReplaceRomModalProps) {
  const [dragOver, setDragOver] = useState(false);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [result, setResult] = useState<ReplaceROMResult | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const replaceRom = useReplaceRom();

  function handleClose() {
    setSelectedFile(null);
    setResult(null);
    setDragOver(false);
    setValidationError(null);
    replaceRom.reset();
    onClose();
  }

  function isValidFile(file: File): boolean {
    const name = file.name.toLowerCase();
    return ACCEPTED_EXTENSIONS.some((ext) => name.endsWith(ext));
  }

  function handleFile(file: File) {
    if (!isValidFile(file)) {
      setValidationError(
        `Unsupported file type. Expected a ROM file (.nes, .sfc, .gba, .zip, etc.)`,
      );
      return;
    }
    setValidationError(null);
    setSelectedFile(file);
    setResult(null);
    replaceRom.mutate(
      { gameId, file },
      {
        onSuccess: (data) => {
          if (data) setResult(data.replacementResult);
        },
      },
    );
  }

  function handleDrop(e: React.DragEvent) {
    e.preventDefault();
    setDragOver(false);
    const file = e.dataTransfer.files[0];
    if (file) handleFile(file);
  }

  function handleDragOver(e: React.DragEvent) {
    e.preventDefault();
    setDragOver(true);
  }

  function handleDragLeave(e: React.DragEvent) {
    e.preventDefault();
    setDragOver(false);
  }

  function handleInputChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (file) handleFile(file);
    // Reset the input so the same file can be re-selected
    e.target.value = "";
  }

  const isUploading = replaceRom.isPending;
  const isError = replaceRom.isError;
  const errorMessage =
    replaceRom.error instanceof Error
      ? replaceRom.error.message
      : "Upload failed";

  return (
    <Modal open={open} onClose={handleClose} title="Replace ROM" size="sm">
      {result ? (
        <ResultView result={result} onClose={handleClose} />
      ) : (
        <div className="space-y-4">
          {/* Drop zone */}
          <button
            type="button"
            data-testid="drop-zone"
            aria-label="Select ROM file to upload"
            className={cn(
              "w-full rounded-2xl border-2 border-dashed p-8 text-center transition-colors cursor-pointer",
              "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500",
              dragOver
                ? "border-brand-500 bg-brand-500/5"
                : "border-surface-700 hover:border-surface-500 bg-surface-800/50",
              isUploading && "pointer-events-none opacity-60",
            )}
            onClick={() => fileInputRef.current?.click()}
            onDrop={handleDrop}
            onDragOver={handleDragOver}
            onDragLeave={handleDragLeave}
          >
            <input
              ref={fileInputRef}
              type="file"
              className="hidden"
              accept={ACCEPT_STRING}
              onChange={handleInputChange}
              data-testid="file-input"
            />
            {isUploading ? (
              <div className="space-y-3">
                <FileUp className="h-10 w-10 mx-auto text-brand-400 animate-pulse" />
                <p className="text-sm font-medium text-surface-200">
                  Uploading {selectedFile?.name}...
                </p>
                <p className="text-xs text-surface-400">
                  {selectedFile && formatFileSize(selectedFile.size)}
                </p>
                <div
                  className="mx-auto w-48 h-1.5 rounded-full bg-surface-700 overflow-hidden"
                  data-testid="upload-progress"
                >
                  <div className="h-full rounded-full bg-brand-500 animate-pulse" style={{ width: "100%" }} />
                </div>
              </div>
            ) : (
              <div className="space-y-3">
                <Upload className="h-10 w-10 mx-auto text-surface-500" />
                <div>
                  <p className="text-sm font-medium text-surface-200">
                    Drop a ROM file here or click to browse
                  </p>
                  <p className="text-xs text-surface-500 mt-1">
                    Supports .zip, .nes, .sfc, .smc, .gba, .gb, .gbc, .md, and
                    more
                  </p>
                </div>
              </div>
            )}
          </button>

          {validationError && (
            <div
              className="flex items-center gap-2 rounded-lg bg-warning-500/10 border border-warning-500/20 px-4 py-3 text-sm text-warning-400"
              data-testid="validation-error"
              role="alert"
            >
              <AlertTriangle className="h-4 w-4 flex-shrink-0" />
              {validationError}
            </div>
          )}

          {isError && (
            <div
              className="flex items-center gap-2 rounded-lg bg-danger-500/10 border border-danger-500/20 px-4 py-3 text-sm text-danger-400"
              data-testid="upload-error"
              role="alert"
            >
              <AlertTriangle className="h-4 w-4 flex-shrink-0" />
              {errorMessage}
            </div>
          )}
        </div>
      )}
    </Modal>
  );
}

function ResultView({
  result,
  onClose,
}: {
  result: ReplaceROMResult;
  onClose: () => void;
}) {
  return (
    <div className="space-y-4" data-testid="replace-result">
      {result.verified ? (
        <div className="flex flex-col items-center gap-3 py-2">
          <CheckCircle2 className="h-12 w-12 text-success-500" />
          <p className="text-lg font-semibold text-surface-100">
            ROM Verified
          </p>
          <Badge variant="success">Verified</Badge>
          {result.canonicalName && (
            <p className="text-sm text-surface-300 text-center">
              {result.canonicalName}
            </p>
          )}
          <p className="text-xs text-surface-500 font-mono">
            CRC32: {result.crc32}
          </p>
        </div>
      ) : (
        <div className="flex flex-col items-center gap-3 py-2">
          <AlertTriangle className="h-12 w-12 text-warning-500" />
          <p className="text-lg font-semibold text-surface-100">
            ROM Replaced
          </p>
          <Badge variant="warning">Unverified</Badge>
          <p className="text-sm text-surface-400 text-center">
            ROM replaced but does not match any known verified dump
          </p>
          <p className="text-xs text-surface-500 font-mono">
            CRC32: {result.crc32}
          </p>
        </div>
      )}

      <div className="flex justify-center pt-2">
        <Button variant="primary" size="sm" onClick={onClose}>
          Close
        </Button>
      </div>
    </div>
  );
}
