import { Gamepad2 } from "lucide-react";
import { Card, CardHeader, CardContent } from "@/components/ui";
import { useGameCheats } from "@/hooks/use-cheats";

export function GameCheats({ gameId }: { gameId: string }) {
  const { data: cheats, isLoading } = useGameCheats(gameId);

  if (isLoading || !cheats || cheats.length === 0) {
    return null;
  }

  return (
    <Card>
      <CardHeader>
        <h2 className="text-lg font-semibold text-surface-100 flex items-center gap-2">
          <Gamepad2 className="h-5 w-5 text-brand-400" />
          Cheat Codes
          <span className="ml-auto text-xs font-medium text-surface-400 bg-surface-800 px-2 py-0.5 rounded-full">
            {cheats.length}
          </span>
        </h2>
      </CardHeader>
      <CardContent>
        <div className="space-y-3">
          {cheats.map((cheat) => (
            <div
              key={cheat.id}
              className="rounded-lg bg-surface-800/50 p-3 space-y-1"
            >
              <p className="text-sm font-medium text-surface-200">
                {cheat.description}
              </p>
              <p className="text-xs font-mono text-surface-400 break-all">
                {cheat.code}
              </p>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
