import type { HTMLAttributes, ReactNode } from "react";
import type { LucideIcon } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { BackButton } from "@/components/ui";

interface PageLayoutProps extends HTMLAttributes<HTMLDivElement> {
  /** Full-width content rendered above the padded area (e.g., hero banners). */
  renderHeader?: () => ReactNode;
  /** Back button variant. Omit for no back button.
   *  - "standard" — renders at the top of the padded content area.
   *  - "floating" — renders floating over the header content (top-left, semi-transparent).
   */
  backButtonVariant?: "standard" | "floating";
  /** Path to navigate to when the back button is clicked. Defaults to browser history back. */
  backTo?: string;
  /** Label for the back button. Defaults to "Back". Example: "Explore", "Consoles". */
  backLabel?: string;
  /** Page title rendered as an h1 in the padded content area. */
  title?: string;
  /** Subtitle rendered below the title. */
  subtitle?: string;
  /** Icon rendered next to the title. */
  icon?: LucideIcon;
  children: ReactNode;
}

/**
 * Core layout component — page-level container with optional full-width header.
 *
 * Renders an optional header at full width (flush to viewport edges), followed
 * by the page content with standard padding. Use `renderHeader` for hero
 * banners or other edge-to-edge content that should not be inside the padded
 * content area.
 *
 * This is a direct child of `<main>` and provides the p-6 content padding.
 */
export function PageLayout({
  renderHeader,
  backButtonVariant,
  backTo,
  backLabel,
  title,
  subtitle,
  icon: Icon,
  children,
  ...rest
}: PageLayoutProps) {
  const navigate = useNavigate();
  const handleBack = () => (backTo ? navigate(backTo) : navigate(-1));

  return (
    <div data-comp="PageLayout" {...rest}>
      {renderHeader && (
        <div className="relative">
          {renderHeader()}
          {backButtonVariant === "floating" && (
            <div className="absolute top-4 left-4 z-20">
              <BackButton
                onClick={handleBack}
                data-testid="page-back-button"
                className="bg-black/40 backdrop-blur-sm text-white/80 hover:text-white hover:bg-black/60"
              >
                {backLabel}
              </BackButton>
            </div>
          )}
        </div>
      )}
      {/*
       * #1071: cap the padded content area at the 2xl breakpoint
       * (1536 px) and centre it. On laptop / standard desktop
       * (≤1536 px) this is a no-op — content fills the viewport as
       * before. On ultrawide / 4K it stops cards from ballooning
       * into 800-px-wide near-empty rectangles. The hero / header
       * surface above stays full-width on purpose so banners flush
       * to the edges.
       */}
      <div className="p-6 mx-auto w-full max-w-screen-2xl">
        {backButtonVariant === "standard" && (
          <div className="mb-6">
            <BackButton onClick={handleBack} data-testid="page-back-button">{backLabel}</BackButton>
          </div>
        )}
        {title && (
          <div className="mb-8">
            <h1 className="text-3xl font-bold text-surface-100 flex items-center gap-3">
              {Icon && <Icon className="h-8 w-8 text-brand-400" />}
              {title}
            </h1>
            {subtitle && (
              <p className="mt-1 text-surface-400">{subtitle}</p>
            )}
          </div>
        )}
        {children}
      </div>
    </div>
  );
}
