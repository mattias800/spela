import { Gamepad2 } from "lucide-react";
import { ConsoleCard } from "@/components/console-card";
import { ConsoleCardSkeleton, EmptyState } from "@/components/ui";
import { useConsoles } from "@/hooks/use-consoles";

export function ConsolesPage() {
  const { data: consoles, isLoading } = useConsoles();

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold text-surface-100">Consoles</h1>
        <p className="mt-1 text-surface-400">
          Browse your game library by platform.
        </p>
      </div>

      {isLoading ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-5">
          {Array.from({ length: 8 }, (_, i) => (
            <ConsoleCardSkeleton key={i} />
          ))}
        </div>
      ) : !consoles || consoles.length === 0 ? (
        <EmptyState
          icon={Gamepad2}
          title="No consoles found"
          description="No game consoles have been detected yet. Run a library scan to discover your games."
        />
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-5">
          {consoles.map((c) => (
            <ConsoleCard key={c.id} console={c} />
          ))}
        </div>
      )}
    </div>
  );
}
