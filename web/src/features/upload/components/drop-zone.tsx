import { useState, useRef } from "react";
import { Upload } from "lucide-react";
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
      <Upload className="h-8 w-8 text-surface-500 mx-auto mb-3" />
      <p className="text-sm text-surface-300 mb-1">
        Drag and drop ROM files here, or click to browse
      </p>
      <p className="text-xs text-surface-500 mb-3">
        Supports .nes, .sfc, .gba, .n64, .nds, .bin, .iso, and more
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
        className="hidden"
        onChange={handleFileChange}
        data-testid="upload-file-input"
      />
    </div>
  );
}
