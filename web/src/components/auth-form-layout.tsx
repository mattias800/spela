import { type ReactNode } from "react";

interface AuthFormLayoutProps {
  title: string;
  subtitle: string;
  error?: string;
  children: ReactNode;
  footer?: ReactNode;
}

export function AuthFormLayout({
  title,
  subtitle,
  error,
  children,
  footer,
}: AuthFormLayoutProps) {
  return (
    <div className="min-h-screen flex items-center justify-center px-4">
      {/* Background gradient */}
      <div className="fixed inset-0 bg-gradient-to-br from-brand-950/40 via-surface-950 to-surface-950" />

      <div className="relative w-full max-w-md space-y-8">
        {/* Logo */}
        <div className="flex flex-col items-center gap-4">
          <img
            src="/api/branding/logo"
            alt="Spela"
            className="h-28 w-auto"
          />
          <div className="text-center">
            <h1 className="text-3xl font-bold tracking-tight text-surface-100">
              {title}
            </h1>
            <p className="mt-2 text-surface-400">{subtitle}</p>
          </div>
        </div>

        {/* Form */}
        <div className="rounded-2xl bg-surface-900/50 border border-surface-800/50 p-8 space-y-5 backdrop-blur-xl">
          {children}
          {error && (
            <div className="rounded-xl bg-danger-500/10 border border-danger-500/30 px-4 py-3 text-sm text-danger-500">
              {error}
            </div>
          )}
          {footer}
        </div>
      </div>
    </div>
  );
}
