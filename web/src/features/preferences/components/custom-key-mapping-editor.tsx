import { KeyCaptureButton } from "@/features/preferences/components/key-capture-button";
import { BUTTONS } from "@/lib/key-mapping-constants";

interface CustomKeyMappingEditorProps {
  mapping: Record<string, string>;
  onChange: (mapping: Record<string, string>) => void;
}

const GROUP_LABELS: Record<string, string> = {
  face: "Face Buttons",
  dpad: "D-Pad",
  shoulder: "Shoulders & Triggers",
  meta: "Meta",
};

const GROUP_ORDER = ["dpad", "face", "shoulder", "meta"];

export function CustomKeyMappingEditor({
  mapping,
  onChange,
}: CustomKeyMappingEditorProps) {
  function handleKeyChange(index: string, key: string) {
    onChange({ ...mapping, [index]: key });
  }

  const groups = GROUP_ORDER.map((group) => ({
    label: GROUP_LABELS[group],
    buttons: BUTTONS.filter((b) => b.group === group),
  }));

  return (
    <div data-comp="CustomKeyMappingEditor" className="space-y-4">
      {groups.map((group) => (
        <div key={group.label}>
          <p className="text-xs font-semibold uppercase tracking-wider text-surface-400 mb-2">
            {group.label}
          </p>
          <div className="grid grid-cols-2 gap-x-6 gap-y-1.5">
            {group.buttons.map((button) => (
              <div
                key={button.index}
                className="flex items-center justify-between"
              >
                <div className="text-sm text-surface-300">{button.label}</div>
                <KeyCaptureButton
                  value={mapping[button.index] ?? ""}
                  onChange={(key) => handleKeyChange(button.index, key)}
                />
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
