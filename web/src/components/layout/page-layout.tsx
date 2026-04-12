import type { HTMLAttributes, ReactNode } from "react";
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
                className="bg-black/40 backdrop-blur-sm text-white/80 hover:text-white hover:bg-black/60"
              />
            </div>
          )}
        </div>
      )}
      <div className="p-6">
        {backButtonVariant === "standard" && (
          <div className="mb-6">
            <BackButton onClick={handleBack} />
          </div>
        )}
        {children}
      </div>
    </div>
  );
}
