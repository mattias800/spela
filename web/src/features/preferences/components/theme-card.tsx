import { Check } from "lucide-react";
import { Card, CardHeader, CardContent, Skeleton } from "@/components/ui";
import { THEME_OPTIONS } from "@/lib/theme-constants";

interface ThemeCardProps {
  selectedTheme: string | undefined;
  isLoading: boolean;
  onThemeChange: (theme: string) => void;
}

export function ThemeCard({
  selectedTheme,
  isLoading,
  onThemeChange,
}: ThemeCardProps) {
  return (
    <Card>
      <CardHeader>
        <h2 className="text-lg font-semibold text-surface-100">Color Theme</h2>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
            {Array.from({ length: 5 }, (_, i) => (
              <Skeleton key={i} className="h-28 w-full rounded-lg" />
            ))}
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
            {THEME_OPTIONS.map((option) => {
              const isSelected =
                (selectedTheme ?? "default-dark") === option.value;
              return (
                <button
                  key={option.value}
                  onClick={() => onThemeChange(option.value)}
                  className={`relative flex flex-col rounded-lg border-2 p-3 text-left transition-all hover:scale-[1.02] ${
                    isSelected
                      ? "border-brand-500 ring-2 ring-brand-500/30"
                      : "border-surface-700 hover:border-surface-500"
                  }`}
                >
                  {isSelected && (
                    <span className="absolute top-2 right-2 flex h-5 w-5 items-center justify-center rounded-full bg-brand-500 text-white">
                      <Check className="h-3 w-3" />
                    </span>
                  )}
                  <div className="mb-2 flex gap-1">
                    {option.swatches.map((color, i) => (
                      <span
                        key={i}
                        className="h-6 w-6 rounded-full border border-surface-700/50"
                        style={{ backgroundColor: color }}
                      />
                    ))}
                  </div>
                  <p className="text-sm font-medium text-surface-100">
                    {option.label}
                  </p>
                  <p className="text-xs text-surface-500">
                    {option.description}
                  </p>
                </button>
              );
            })}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
