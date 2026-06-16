import { Link } from "react-router-dom";
import { ConsoleBadge } from "@/components/console-badge";
import { CoverImage } from "@/components/cover-image";
import type { CatalogAvailability } from "@/generated/schemas";

// Card for a connected-server game (browse grid). Mirrors GameCard's cover
// treatment but carries no local metadata — it links to the remote-game page,
// passing the catalog entry as router state so that page renders instantly.
export function RemoteGameCard({ game }: { game: CatalogAvailability }) {
  const servers = game.originCount;
  return (
    <Link
      to={`/remote-games/${encodeURIComponent(game.key)}`}
      state={game}
      data-testid={`remote-game-card-${game.key}`}
      className="group block space-y-3"
    >
      <div className="relative overflow-hidden rounded-2xl border border-surface-800/50 transition-all duration-300 group-hover:-translate-y-1 group-hover:border-surface-700/50 group-hover:shadow-xl group-hover:shadow-black/30">
        <CoverImage
          src={game.cover || null}
          alt={game.title}
          className="aspect-[3/4] w-full transition-transform duration-500 group-hover:scale-105"
        />
        <div className="absolute bottom-2.5 left-2.5">
          <ConsoleBadge code={game.console} />
        </div>
      </div>

      <div className="space-y-1 px-1">
        <h3 className="truncate text-sm font-semibold text-surface-100 transition-colors group-hover:text-brand-400">
          {game.title}
        </h3>
        <p className="text-xs text-surface-500">
          on {servers} connected {servers === 1 ? "server" : "servers"}
        </p>
      </div>
    </Link>
  );
}
