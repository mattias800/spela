import { Select } from "@/components/ui";
import type { StagedUpload } from "@/types/api";

interface ConsoleSelectorProps {
  upload: StagedUpload;
  onSelect: (consoleId: string) => void;
  isPending: boolean;
}

export function ConsoleSelector({
  upload,
  onSelect,
  isPending,
}: ConsoleSelectorProps) {
  const options = [
    { value: "", label: "Select console..." },
    ...upload.possibleConsoles.map((c) => ({
      value: c.id,
      label: c.name,
    })),
  ];

  return (
    <div className="flex items-center gap-2" data-testid="console-selector">
      <Select
        options={options}
        value=""
        onChange={(e) => {
          if (e.target.value) {
            onSelect(e.target.value);
          }
        }}
        disabled={isPending}
        className="text-xs"
      />
    </div>
  );
}
