import { type ReactNode } from "react";
import { Gamepad2 } from "lucide-react";

interface AuthFormLayoutProps {
  title: string;
  subtitle: string;
  error?: string;
  children: ReactNode;
  footer?: ReactNode;
}

export function AuthFormLayout({ title, subtitle, error, children, footer }: AuthFormLayoutProps) {
  return (
    <div className="min-h-screen flex items-center justify-center px-4">
      {/* Background gradient */}
      <div className="fixed inset-0 bg-gradient-to-br from-brand-950/40 via-surface-950 to-surface-950" />

      <div className="relative w-full max-w-md space-y-8">
        {/* Logo */}
        <div className="flex flex-col items-center gap-4">
          <div className="h-16 w-16 rounded-2xl bg-gradient-to-br from-brand-500 to-brand-700 flex items-center justify-center shadow-xl shadow-brand-600/30">
            <Gamepad2 className="h-8 w-8 text-white" />
          </div>
          <div className="text-center">
            <h1 className="text-3xl font-bold tracking-tight text-surface-100">
              {title}
            </h1>
            <p className="mt-2 text-surface-400">
              {subtitle}
            </p>
          </div>
        </div>

        {/* Form */}
        <div className="rounded-2xl bg-surface-900/50 border border-surface-800/50 p-8 space-y-5 backdrop-blur-xl">
          {error && (
            <div className="rounded-xl bg-danger-500/10 border border-danger-500/30 px-4 py-3 text-sm text-danger-500">
              {error}
            </div>
          )}
          {children}
          {footer}
        </div>
      </div>
    </div>
  );
}
