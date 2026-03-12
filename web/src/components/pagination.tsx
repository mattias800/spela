import { ChevronLeft, ChevronRight } from "lucide-react";
import { cn } from "@/lib/cn";

interface PaginationProps {
  total: number;
  pageSize: number;
  currentPage: number;
  onPageChange: (page: number) => void;
}

function getPageNumbers(currentPage: number, pageCount: number): (number | "ellipsis")[] {
  // Show all pages when total is small
  if (pageCount <= 7) {
    return Array.from({ length: pageCount }, (_, i) => i + 1);
  }

  const pages: (number | "ellipsis")[] = [];

  // Always include first page
  pages.push(1);

  // Calculate window around current page
  const windowStart = Math.max(2, currentPage - 1);
  const windowEnd = Math.min(pageCount - 1, currentPage + 1);

  // Add left ellipsis if needed
  if (windowStart > 2) {
    pages.push("ellipsis");
  }

  // Add pages in the window
  for (let i = windowStart; i <= windowEnd; i++) {
    pages.push(i);
  }

  // Add right ellipsis if needed
  if (windowEnd < pageCount - 1) {
    pages.push("ellipsis");
  }

  // Always include last page
  pages.push(pageCount);

  return pages;
}

export function Pagination({
  total,
  pageSize,
  currentPage,
  onPageChange,
}: PaginationProps) {
  if (total <= pageSize) return null;

  const pageCount = Math.ceil(total / pageSize);
  const pages = getPageNumbers(currentPage, pageCount);

  return (
    <div className="flex justify-center items-center gap-1.5 pt-4" data-testid="pagination">
      {/* Previous button */}
      <button
        onClick={() => onPageChange(currentPage - 1)}
        disabled={currentPage <= 1}
        className={cn(
          "h-9 w-9 rounded-lg text-sm font-medium transition-colors flex items-center justify-center",
          currentPage <= 1
            ? "text-surface-600 cursor-not-allowed"
            : "bg-surface-900 text-surface-400 hover:text-surface-100 hover:bg-surface-800",
        )}
        aria-label="Previous page"
        data-testid="pagination-prev"
      >
        <ChevronLeft className="h-4 w-4" />
      </button>

      {/* Page buttons */}
      {pages.map((page, index) =>
        page === "ellipsis" ? (
          <span
            key={`ellipsis-${index}`}
            className="h-9 w-9 flex items-center justify-center text-sm text-surface-500"
            data-testid="pagination-ellipsis"
          >
            ...
          </span>
        ) : (
          <button
            key={page}
            onClick={() => onPageChange(page)}
            className={cn(
              "h-9 w-9 rounded-lg text-sm font-medium transition-colors",
              currentPage === page
                ? "bg-brand-600 text-white"
                : "bg-surface-900 text-surface-400 hover:text-surface-100 hover:bg-surface-800",
            )}
            aria-current={currentPage === page ? "page" : undefined}
            data-testid={`pagination-page-${page}`}
          >
            {page}
          </button>
        ),
      )}

      {/* Next button */}
      <button
        onClick={() => onPageChange(currentPage + 1)}
        disabled={currentPage >= pageCount}
        className={cn(
          "h-9 w-9 rounded-lg text-sm font-medium transition-colors flex items-center justify-center",
          currentPage >= pageCount
            ? "text-surface-600 cursor-not-allowed"
            : "bg-surface-900 text-surface-400 hover:text-surface-100 hover:bg-surface-800",
        )}
        aria-label="Next page"
        data-testid="pagination-next"
      >
        <ChevronRight className="h-4 w-4" />
      </button>
    </div>
  );
}
