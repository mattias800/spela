import type { HTMLAttributes, ReactNode } from "react";

interface PageLayoutProps extends HTMLAttributes<HTMLDivElement> {
  /** Full-width content rendered above the padded area (e.g., hero banners). */
  renderHeader?: () => ReactNode;
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
export function PageLayout({ renderHeader, children, ...rest }: PageLayoutProps) {
  return (
    <div data-comp="PageLayout" {...rest}>
      {renderHeader?.()}
      <div className="p-6">
        {children}
      </div>
    </div>
  );
}
