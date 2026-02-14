import { cn } from "@/lib/cn";

interface PaginationProps {
  total: number;
  pageSize: number;
  currentPage: number;
  onPageChange: (page: number) => void;
}

export function Pagination({
  total,
  pageSize,
  currentPage,
  onPageChange,
}: PaginationProps) {
  if (total <= pageSize) return null;

  const pageCount = Math.ceil(total / pageSize);

  return (
    <div className="flex justify-center gap-2 pt-4">
      {Array.from({ length: pageCount }, (_, i) => (
        <button
          key={i}
          onClick={() => onPageChange(i + 1)}
          className={cn(
            "h-9 w-9 rounded-lg text-sm font-medium transition-colors",
            currentPage === i + 1
              ? "bg-brand-600 text-white"
              : "bg-surface-900 text-surface-400 hover:text-surface-100 hover:bg-surface-800",
          )}
        >
          {i + 1}
        </button>
      ))}
    </div>
  );
}
