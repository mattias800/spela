import {
  Gamepad2,
  Calendar,
  Tag,
  Monitor,
  Star,
} from "lucide-react";
import type { ActiveYears } from "@/types/api";

interface AtAGlanceRowProps {
  gameCount: number;
  activeYears?: ActiveYears;
  primaryGenre?: string;
  platformCount: number;
  avgRating: number;
}

interface StatPillProps {
  icon: React.ReactNode;
  label: string;
  value: string;
  testId: string;
}

function StatPill({ icon, label, value, testId }: StatPillProps) {
  return (
    <div
      className="flex items-center gap-2.5 rounded-xl border border-white/[0.06] bg-surface-900/60 px-4 py-2.5"
      data-testid={testId}
    >
      <div className="flex-shrink-0 text-surface-400">{icon}</div>
      <div className="min-w-0">
        <p className="text-sm font-semibold text-surface-100 truncate">
          {value}
        </p>
        <p className="text-xs text-surface-500">{label}</p>
      </div>
    </div>
  );
}

export function AtAGlanceRow({
  gameCount,
  activeYears,
  primaryGenre,
  platformCount,
  avgRating,
}: AtAGlanceRowProps) {
  const pills: StatPillProps[] = [];

  pills.push({
    icon: <Gamepad2 className="h-4 w-4" />,
    label: "Total games",
    value: String(gameCount),
    testId: "glance-total-games",
  });

  if (activeYears) {
    const yearStr =
      activeYears.first === activeYears.last
        ? String(activeYears.first)
        : `${activeYears.first} \u2013 ${activeYears.last}`;
    pills.push({
      icon: <Calendar className="h-4 w-4" />,
      label: "Active years",
      value: yearStr,
      testId: "glance-active-years",
    });
  }

  if (primaryGenre) {
    pills.push({
      icon: <Tag className="h-4 w-4" />,
      label: "Primary genre",
      value: primaryGenre,
      testId: "glance-primary-genre",
    });
  }

  if (platformCount > 0) {
    pills.push({
      icon: <Monitor className="h-4 w-4" />,
      label: platformCount === 1 ? "Platform" : "Platforms",
      value: String(platformCount),
      testId: "glance-platforms",
    });
  }

  if (avgRating > 0) {
    pills.push({
      icon: <Star className="h-4 w-4 text-amber-400 fill-amber-400" />,
      label: "Avg rating",
      value: avgRating.toFixed(1),
      testId: "glance-avg-rating",
    });
  }

  if (pills.length === 0) return null;

  return (
    <section data-testid="at-a-glance-row">
      <div className="flex flex-wrap gap-3">
        {pills.map((pill) => (
          <StatPill key={pill.testId} {...pill} />
        ))}
      </div>
    </section>
  );
}
