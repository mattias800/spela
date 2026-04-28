import { useMemo, useState } from "react";
import type { DailyPlayActivity } from "@/types/api";
import { formatPlayTime } from "@/lib/format";
import { cn } from "@/lib/cn";

interface PlayHeatmapProps {
  data: DailyPlayActivity[];
  className?: string;
}

const CELL_SIZE = 13;
const CELL_GAP = 3;
const CELL_RADIUS = 2;
const WEEKS = 52;
const DAYS = 7;
const LABEL_WIDTH = 30;
const TOP_MARGIN = 18;

const DAY_LABELS = [
  { day: 1, label: "Mon" },
  { day: 3, label: "Wed" },
  { day: 5, label: "Fri" },
];

const MONTH_NAMES = [
  "Jan",
  "Feb",
  "Mar",
  "Apr",
  "May",
  "Jun",
  "Jul",
  "Aug",
  "Sep",
  "Oct",
  "Nov",
  "Dec",
];

const COLORS = [
  "var(--color-surface-800)",
  "var(--color-brand-900)",
  "var(--color-brand-700)",
  "var(--color-brand-500)",
  "var(--color-brand-400)",
];

function getColorLevel(playTime: number, maxTime: number): number {
  if (playTime === 0) return 0;
  if (maxTime === 0) return 0;
  const ratio = playTime / maxTime;
  if (ratio <= 0.25) return 1;
  if (ratio <= 0.5) return 2;
  if (ratio <= 0.75) return 3;
  return 4;
}

function formatDateLabel(date: Date): string {
  return date.toLocaleDateString(undefined, {
    weekday: "long",
    year: "numeric",
    month: "long",
    day: "numeric",
  });
}

function toDateKey(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

export function PlayHeatmap({ data, className }: PlayHeatmapProps) {
  const [tooltip, setTooltip] = useState<{
    x: number;
    y: number;
    date: Date;
    playTime: number;
  } | null>(null);

  const { cells, monthLabels, maxTime } = useMemo(() => {
    const activityMap = new Map<string, number>();
    for (const entry of data) {
      activityMap.set(entry.date, entry.playTime);
    }

    const today = new Date();
    today.setHours(0, 0, 0, 0);

    // Find the end of the current week (Saturday)
    const endDay = new Date(today);
    const todayDow = today.getDay(); // 0=Sun
    const daysUntilSat = (6 - todayDow + 7) % 7;
    endDay.setDate(endDay.getDate() + daysUntilSat);

    // Go back WEEKS weeks from the end day to find the start (Sunday)
    const startDay = new Date(endDay);
    startDay.setDate(startDay.getDate() - WEEKS * 7 + 1);

    // Ensure startDay is a Sunday
    const startDow = startDay.getDay();
    if (startDow !== 0) {
      startDay.setDate(startDay.getDate() - startDow);
    }

    let max = 0;
    const cellList: {
      date: Date;
      week: number;
      day: number;
      playTime: number;
    }[] = [];

    const current = new Date(startDay);
    while (current <= endDay) {
      const key = toDateKey(current);
      const playTime = activityMap.get(key) ?? 0;
      if (playTime > max) max = playTime;

      // Use UTC to avoid DST shifts when computing day offsets
      const diffMs =
        Date.UTC(
          current.getFullYear(),
          current.getMonth(),
          current.getDate(),
        ) -
        Date.UTC(
          startDay.getFullYear(),
          startDay.getMonth(),
          startDay.getDate(),
        );
      const diffDays = Math.round(diffMs / 86400000);
      const week = Math.floor(diffDays / 7);
      const day = diffDays % 7;

      cellList.push({
        date: new Date(current),
        week,
        day,
        playTime,
      });

      current.setDate(current.getDate() + 1);
    }

    // Compute month labels
    const labels: { week: number; label: string }[] = [];
    let lastMonth = -1;
    for (const cell of cellList) {
      if (cell.day === 0) {
        const month = cell.date.getMonth();
        if (month !== lastMonth) {
          labels.push({ week: cell.week, label: MONTH_NAMES[month] });
          lastMonth = month;
        }
      }
    }

    return { cells: cellList, monthLabels: labels, maxTime: max };
  }, [data]);

  if (data.length === 0) {
    return (
      <div className={cn("text-surface-500 text-sm py-8 text-center", className)}>
        No play activity yet
      </div>
    );
  }

  const totalWeeks = Math.max(...cells.map((c) => c.week)) + 1;
  const svgWidth = LABEL_WIDTH + totalWeeks * (CELL_SIZE + CELL_GAP);
  const svgHeight = TOP_MARGIN + DAYS * (CELL_SIZE + CELL_GAP);

  return (
    <div data-comp="PlayHeatmap" className={cn("relative", className)}>
      <div className="overflow-x-auto">
        <svg
          width={svgWidth}
          height={svgHeight + 30}
          className="block"
          role="img"
          aria-label="Play activity heatmap"
        >
          {/* Month labels */}
          {monthLabels.map(({ week, label }) => (
            <text
              key={`month-${week}`}
              x={LABEL_WIDTH + week * (CELL_SIZE + CELL_GAP)}
              y={12}
              className="fill-surface-500"
              fontSize={10}
            >
              {label}
            </text>
          ))}

          {/* Day labels */}
          {DAY_LABELS.map(({ day, label }) => (
            <text
              key={`day-${day}`}
              x={0}
              y={TOP_MARGIN + day * (CELL_SIZE + CELL_GAP) + CELL_SIZE - 2}
              className="fill-surface-500"
              fontSize={10}
            >
              {label}
            </text>
          ))}

          {/* Cells */}
          {cells.map((cell) => {
            const level = getColorLevel(cell.playTime, maxTime);
            const x = LABEL_WIDTH + cell.week * (CELL_SIZE + CELL_GAP);
            const y = TOP_MARGIN + cell.day * (CELL_SIZE + CELL_GAP);

            return (
              <rect
                key={toDateKey(cell.date)}
                x={x}
                y={y}
                width={CELL_SIZE}
                height={CELL_SIZE}
                rx={CELL_RADIUS}
                ry={CELL_RADIUS}
                fill={COLORS[level]}
                data-testid="heatmap-cell"
                data-date={toDateKey(cell.date)}
                data-level={level}
                onMouseEnter={(e) => {
                  const rect = (
                    e.target as SVGRectElement
                  ).getBoundingClientRect();
                  setTooltip({
                    x: rect.left + rect.width / 2,
                    y: rect.top,
                    date: cell.date,
                    playTime: cell.playTime,
                  });
                }}
                onMouseLeave={() => setTooltip(null)}
              />
            );
          })}

          {/* Legend */}
          <text
            x={svgWidth - 130}
            y={svgHeight + 22}
            className="fill-surface-500"
            fontSize={10}
          >
            Less
          </text>
          {COLORS.map((color, i) => (
            <rect
              key={`legend-${i}`}
              x={svgWidth - 100 + i * (CELL_SIZE + 2)}
              y={svgHeight + 12}
              width={CELL_SIZE}
              height={CELL_SIZE}
              rx={CELL_RADIUS}
              ry={CELL_RADIUS}
              fill={color}
            />
          ))}
          <text
            x={svgWidth - 100 + COLORS.length * (CELL_SIZE + 2) + 4}
            y={svgHeight + 22}
            className="fill-surface-500"
            fontSize={10}
          >
            More
          </text>
        </svg>
      </div>

      {/* Tooltip */}
      {tooltip && (
        <div
          className="fixed z-50 pointer-events-none px-2.5 py-1.5 rounded-lg bg-surface-900 border border-surface-700 text-xs text-surface-200 shadow-lg whitespace-nowrap"
          style={{
            left: tooltip.x,
            top: tooltip.y - 8,
            transform: "translate(-50%, -100%)",
          }}
        >
          <span className="font-medium">
            {tooltip.playTime > 0
              ? formatPlayTime(tooltip.playTime)
              : "No activity"}
          </span>
          <span className="text-surface-400 ml-1.5">
            {formatDateLabel(tooltip.date)}
          </span>
        </div>
      )}
    </div>
  );
}
