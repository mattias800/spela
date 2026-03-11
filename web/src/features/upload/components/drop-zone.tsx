import { useState, useRef } from "react";
import { Upload, Archive } from "lucide-react";
import { Button } from "@/components/ui";

interface DropZoneProps {
  onFiles: (files: File[]) => void;
  isUploading: boolean;
}

export function DropZone({ onFiles, isUploading }: DropZoneProps) {
  const [isDragOver, setIsDragOver] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  function handleDrop(e: React.DragEvent) {
    e.preventDefault();
    setIsDragOver(false);
    if (isUploading) return;
    if (e.dataTransfer.files.length > 0) {
      onFiles(Array.from(e.dataTransfer.files));
    }
  }

  function handleDragOver(e: React.DragEvent) {
    e.preventDefault();
    setIsDragOver(true);
  }

  function handleDragLeave() {
    setIsDragOver(false);
  }

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    if (isUploading) return;
    if (e.target.files && e.target.files.length > 0) {
      onFiles(Array.from(e.target.files));
      e.target.value = "";
    }
  }

  return (
    <div
      className={`rounded-2xl border-2 border-dashed p-8 text-center transition-colors ${
        isDragOver
          ? "border-brand-500 bg-brand-500/5"
          : "border-surface-700 hover:border-surface-600"
      }`}
      onDrop={handleDrop}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      data-testid="upload-drop-zone"
    >
      <div className="flex items-center justify-center gap-2 mb-3">
        <Upload className="h-7 w-7 text-surface-500" />
        <Archive className="h-7 w-7 text-surface-500" />
      </div>
      <p className="text-sm text-surface-300 mb-1">
        Drop ROM files or ZIP archives here, or click to browse
      </p>
      <p className="text-xs text-surface-500 mb-3">
        Supports .nes, .sfc, .gba, .n64, .nds, .bin, .iso, .zip, and more
      </p>
      <Button
        onClick={() => fileInputRef.current?.click()}
        loading={isUploading}
        icon={<Upload className="h-4 w-4" />}
      >
        Select Files
      </Button>
      <input
        ref={fileInputRef}
        type="file"
        multiple
        accept=".nes,.fds,.sfc,.smc,.gb,.gbc,.gba,.n64,.z64,.v64,.nds,.sms,.md,.gen,.pce,.a26,.cso,.pbp,.cue,.gdi,.cdi,.3ds,.cci,.cia,.gcm,.gcz,.rvz,.ciso,.xex,.god,.wbfs,.rpx,.wud,.wux,.nsp,.xci,.bin,.iso,.pkg,.xvd,.chd,.m3u,.zip,.7z"
        className="hidden"
        onChange={handleFileChange}
        data-testid="upload-file-input"
      />
    </div>
  );
}
