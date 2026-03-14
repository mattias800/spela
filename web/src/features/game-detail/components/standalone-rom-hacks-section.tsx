import { Link } from "react-router-dom";
import { Puzzle, Gamepad2 } from "lucide-react";
import { Card } from "@/components/ui";

interface RomHackEntry {
  id: string;
  title: string;
  coverUrl: string;
}

interface StandaloneRomHacksSectionProps {
  romHacks: RomHackEntry[];
}

export function StandaloneRomHacksSection({
  romHacks,
}: StandaloneRomHacksSectionProps) {
  if (!romHacks || romHacks.length === 0) return null;

  return (
    <Card className="p-6" data-testid="standalone-rom-hacks-section">
      <div className="flex items-center gap-2.5 mb-4">
        <Puzzle className="h-5 w-5 text-brand-400" />
        <h2 className="text-lg font-semibold text-surface-100">ROM Hacks</h2>
        <span className="text-sm text-surface-500">({romHacks.length})</span>
      </div>
      <div className="space-y-2">
        {romHacks.map((hack) => (
          <Link
            key={hack.id}
            to={`/games/${hack.id}`}
            className="flex items-center gap-3 px-4 py-3 rounded-xl bg-surface-800/30 hover:bg-surface-800/50 transition-colors group focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
            data-testid={`rom-hack-${hack.id}`}
          >
            {hack.coverUrl ? (
              <img
                src={hack.coverUrl}
                alt={hack.title}
                className="h-12 w-9 rounded object-cover flex-shrink-0"
              />
            ) : (
              <div className="h-12 w-9 rounded bg-surface-800 flex items-center justify-center flex-shrink-0">
                <Gamepad2 className="h-4 w-4 text-surface-600" />
              </div>
            )}
            <p className="text-sm font-medium text-surface-200 truncate group-hover:text-brand-400 transition-colors">
              {hack.title}
            </p>
          </Link>
        ))}
      </div>
    </Card>
  );
}
