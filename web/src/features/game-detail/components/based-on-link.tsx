import { Link } from "react-router-dom";
import { Gamepad2 } from "lucide-react";

interface BasedOnLinkProps {
  parentGame: {
    id: string;
    title: string;
    coverUrl?: string;
  };
}

export function BasedOnLink({ parentGame }: BasedOnLinkProps) {
  return (
    <Link
      to={`/games/${parentGame.id}`}
      className="inline-flex items-center gap-3 text-sm text-surface-300 hover:text-brand-400 transition-colors bg-surface-900/60 border border-surface-800/50 rounded-lg px-3 py-2 hover:border-surface-700/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
      data-testid="based-on-link"
    >
      {parentGame.coverUrl ? (
        <img
          src={parentGame.coverUrl}
          alt={parentGame.title}
          className="h-8 w-6 rounded object-cover flex-shrink-0"
        />
      ) : (
        <div className="h-8 w-6 rounded bg-surface-800 flex items-center justify-center flex-shrink-0">
          <Gamepad2 className="h-3 w-3 text-surface-600" />
        </div>
      )}
      <span>
        Based on{" "}
        <span className="font-semibold text-surface-100">
          {parentGame.title}
        </span>
      </span>
    </Link>
  );
}
