import { formatKeyName } from "@/lib/key-mapping-constants";

interface ControllerVisualProps {
  mapping: Record<string, string>;
}

function KeyBadge({ label, keyName }: { label: string; keyName: string }) {
  return (
    <div data-comp="KeyBadge" className="flex items-center gap-2">
      <span className="text-xs text-surface-400 w-20 text-right">{label}</span>
      <span className="inline-flex items-center justify-center min-w-[2rem] px-1.5 py-0.5 rounded bg-surface-800 border border-surface-700 text-xs font-mono text-surface-200">
        {formatKeyName(keyName)}
      </span>
    </div>
  );
}

export function ControllerVisual({ mapping }: ControllerVisualProps) {
  return (
    <div data-comp="ControllerVisual"
      data-testid="controller-visual"
      className="bg-surface-900 border border-surface-800 rounded-xl p-5"
    >
      {/* Triggers */}
      <div className="flex justify-between mb-3 px-8">
        <div className="flex gap-4">
          <KeyBadge label="L2" keyName={mapping["12"] ?? ""} />
          <KeyBadge label="L1" keyName={mapping["10"] ?? ""} />
        </div>
        <div className="flex gap-4">
          <KeyBadge label="R1" keyName={mapping["11"] ?? ""} />
          <KeyBadge label="R2" keyName={mapping["13"] ?? ""} />
        </div>
      </div>

      {/* Main body */}
      <div className="flex justify-between items-center px-4">
        {/* D-pad */}
        <div className="flex flex-col items-center gap-0.5">
          <span className="text-[10px] uppercase tracking-wider text-surface-500 mb-1">
            D-Pad
          </span>
          <div className="grid grid-cols-3 gap-0.5 w-24">
            <div />
            <div className="flex items-center justify-center h-7 rounded bg-surface-800 border border-surface-700 text-xs font-mono text-surface-200">
              {formatKeyName(mapping["4"] ?? "")}
            </div>
            <div />
            <div className="flex items-center justify-center h-7 rounded bg-surface-800 border border-surface-700 text-xs font-mono text-surface-200">
              {formatKeyName(mapping["6"] ?? "")}
            </div>
            <div className="h-7 bg-surface-800/50 rounded" />
            <div className="flex items-center justify-center h-7 rounded bg-surface-800 border border-surface-700 text-xs font-mono text-surface-200">
              {formatKeyName(mapping["7"] ?? "")}
            </div>
            <div />
            <div className="flex items-center justify-center h-7 rounded bg-surface-800 border border-surface-700 text-xs font-mono text-surface-200">
              {formatKeyName(mapping["5"] ?? "")}
            </div>
            <div />
          </div>
        </div>

        {/* Select / Start */}
        <div className="flex flex-col items-center gap-2">
          <div className="flex gap-3">
            <div className="text-center">
              <span className="text-[10px] uppercase tracking-wider text-surface-500 block mb-1">
                Select
              </span>
              <span className="inline-flex items-center justify-center min-w-[2.5rem] px-1.5 py-1 rounded bg-surface-800 border border-surface-700 text-xs font-mono text-surface-200">
                {formatKeyName(mapping["2"] ?? "")}
              </span>
            </div>
            <div className="text-center">
              <span className="text-[10px] uppercase tracking-wider text-surface-500 block mb-1">
                Start
              </span>
              <span className="inline-flex items-center justify-center min-w-[2.5rem] px-1.5 py-1 rounded bg-surface-800 border border-surface-700 text-xs font-mono text-surface-200">
                {formatKeyName(mapping["3"] ?? "")}
              </span>
            </div>
          </div>
        </div>

        {/* Face buttons */}
        <div className="flex flex-col items-center gap-0.5">
          <span className="text-[10px] uppercase tracking-wider text-surface-500 mb-1">
            Face
          </span>
          <div className="grid grid-cols-3 gap-0.5 w-24">
            <div />
            <div className="flex items-center justify-center h-7 rounded bg-surface-800 border border-surface-700 text-xs font-mono text-surface-200">
              {formatKeyName(mapping["9"] ?? "")}
            </div>
            <div />
            <div className="flex items-center justify-center h-7 rounded bg-surface-800 border border-surface-700 text-xs font-mono text-surface-200">
              {formatKeyName(mapping["8"] ?? "")}
            </div>
            <div className="h-7 bg-surface-800/50 rounded" />
            <div className="flex items-center justify-center h-7 rounded bg-surface-800 border border-surface-700 text-xs font-mono text-surface-200">
              {formatKeyName(mapping["1"] ?? "")}
            </div>
            <div />
            <div className="flex items-center justify-center h-7 rounded bg-surface-800 border border-surface-700 text-xs font-mono text-surface-200">
              {formatKeyName(mapping["0"] ?? "")}
            </div>
            <div />
          </div>
        </div>
      </div>
    </div>
  );
}
