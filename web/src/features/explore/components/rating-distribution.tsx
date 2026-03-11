import type { RatingDistribution as RatingDistributionType } from "@/types/api";

interface RatingDistributionProps {
  distribution: RatingDistributionType;
}

interface BarEntry {
  label: string;
  count: number;
  color: string;
  testId: string;
}

export function RatingDistribution({ distribution }: RatingDistributionProps) {
  // Only show when at least 5 games have ratings
  const ratedTotal =
    distribution.excellent +
    distribution.good +
    distribution.average +
    distribution.poor;

  if (ratedTotal < 5) return null;

  const totalWithUnrated = ratedTotal + distribution.unrated;

  const bars: BarEntry[] = [
    {
      label: "Excellent",
      count: distribution.excellent,
      color: "bg-emerald-500",
      testId: "rating-bar-excellent",
    },
    {
      label: "Good",
      count: distribution.good,
      color: "bg-yellow-500",
      testId: "rating-bar-good",
    },
    {
      label: "Average",
      count: distribution.average,
      color: "bg-orange-500",
      testId: "rating-bar-average",
    },
    {
      label: "Poor",
      count: distribution.poor,
      color: "bg-red-500",
      testId: "rating-bar-poor",
    },
    {
      label: "Unrated",
      count: distribution.unrated,
      color: "bg-surface-600",
      testId: "rating-bar-unrated",
    },
  ];

  // Filter out zero-count bars
  const visibleBars = bars.filter((b) => b.count > 0);

  return (
    <section data-testid="rating-distribution">
      <h2 className="text-lg font-bold text-surface-100 mb-4">
        Rating Distribution
      </h2>
      <div className="space-y-2.5">
        {visibleBars.map((bar) => {
          const pct =
            totalWithUnrated > 0
              ? (bar.count / totalWithUnrated) * 100
              : 0;
          return (
            <div
              key={bar.label}
              className="flex items-center gap-3"
              data-testid={bar.testId}
            >
              <span className="w-20 text-xs text-surface-400 text-right flex-shrink-0">
                {bar.label}
              </span>
              <div className="flex-1 h-5 bg-surface-800 rounded-full overflow-hidden">
                <div
                  className={`h-full ${bar.color} rounded-full transition-all duration-300`}
                  style={{ width: `${Math.max(pct, 1)}%` }}
                />
              </div>
              <span className="w-8 text-xs text-surface-300 font-medium text-right flex-shrink-0">
                {bar.count}
              </span>
            </div>
          );
        })}
      </div>
    </section>
  );
}
