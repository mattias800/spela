import { type ReactNode } from "react";
import { Gamepad2, Check } from "lucide-react";

const STEPS = ["System Check", "Account", "IGDB", "Games", "Done"];

interface WizardLayoutProps {
  currentStep: number;
  children: ReactNode;
}

export function WizardLayout({ currentStep, children }: WizardLayoutProps) {
  return (
    <div className="min-h-screen flex flex-col items-center px-4 py-8">
      <div className="fixed inset-0 bg-gradient-to-br from-brand-950/40 via-surface-950 to-surface-950" />

      <div className="relative w-full max-w-2xl space-y-8">
        {/* Logo */}
        <div className="flex flex-col items-center gap-4">
          <div className="h-16 w-16 rounded-2xl bg-gradient-to-br from-brand-500 to-brand-700 flex items-center justify-center shadow-xl shadow-brand-600/30">
            <Gamepad2 className="h-8 w-8 text-white" />
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-surface-100">
            Setup Spela
          </h1>
        </div>

        {/* Step indicator */}
        <div className="flex items-center justify-center gap-0">
          {STEPS.map((label, i) => (
            <div key={label} className="flex items-center">
              {i > 0 && (
                <div
                  className={`w-8 h-px mx-1 ${i <= currentStep ? "bg-brand-500" : "bg-surface-700"}`}
                />
              )}
              <div className="flex flex-col items-center gap-1.5">
                <div
                  className={`h-8 w-8 rounded-full flex items-center justify-center text-xs font-semibold transition-colors ${
                    i < currentStep
                      ? "bg-brand-500 text-white"
                      : i === currentStep
                        ? "bg-brand-500 text-white ring-2 ring-brand-400/50"
                        : "bg-surface-800 text-surface-500"
                  }`}
                >
                  {i < currentStep ? <Check className="h-4 w-4" /> : i + 1}
                </div>
                <span
                  className={`text-xs whitespace-nowrap ${
                    i <= currentStep
                      ? "text-surface-300"
                      : "text-surface-600"
                  }`}
                >
                  {label}
                </span>
              </div>
            </div>
          ))}
        </div>

        {/* Content */}
        <div className="rounded-2xl bg-surface-900/50 border border-surface-800/50 p-8 backdrop-blur-xl">
          {children}
        </div>
      </div>
    </div>
  );
}
